package com.redhat.runtimes;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.config.ConfigChange;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.Counter;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.sstore.ClusteredSessionStore;
import io.vertx.ext.web.sstore.SessionStore;

public class MainVerticle extends AbstractVerticle {
    private static final String INSTANCE_ID = UUID.randomUUID().toString();
    private static final String REQUEST_COUNTER = "requestCount";

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    Counter requestCounter;
    private final JsonObject currentConfig = new JsonObject();
    private ConfigRetrieverOptions addStore;

    @Override
    public void start(final Promise<Void> startPromise) {
        currentConfig.put("appname", "J4K Vert.x Demo");
        currentConfig.put("port", 8080);
        currentConfig.mergeIn(config());

        // Check to see if this application is running inside of a Kubernetes
        // cluster by looking to see if there is a service account token.
        vertx.fileSystem().exists("/run/secrets/kubernetes.io/serviceaccount/token")
            .onSuccess(this::initConfigWatcher);

        LOG.info("Creating Router");
        final Router router = Router.router(vertx);

        router.route().handler(this::logAllRequests);

        // Use the clustered session implementation
        router.route().handler(clusteredSession());

        // Create a GET endpoint
        router.get("/api/healthz").handler(this::healthCheck);
        router.get("/api/podinfo").handler(this::podInfoHandler);

        // Enable all other endpoints to serve static content from src/main/resources/webroot
        router.route().handler(configStaticHandler());

        LOG.info("Create EventBus Bridge");
        router.mountSubRouter("/api/eventbus", sockJsBridge());

        LOG.info("Creating HTTP Server");
        HttpServer server = vertx.createHttpServer()
            .requestHandler(router);

        // Start the HTTP Server
        server.listen(currentConfig.getInteger("port", 80))
            .compose(this::initSharedData)                  // Initialize a shared data structure in the cluster
            .onSuccess(counter -> {
                requestCounter = counter;
                LOG.info("Setting up periodic status messages");
                vertx.setPeriodic(400, this::sendPeriodic); // Set up a periodic eventbus message broadcast
            })
            .onComplete(res -> startPromise.complete())     // Let Vert.x know our Verticle is running
            .onFailure(startPromise::fail);                 // OR let Vert.x know our Verticle failed
    }

    private void logAllRequests(RoutingContext ctx) {
        LOG.info("Request: {}", ctx.request().path());
        ctx.next();
    }

    /**
     * Simple health check endpoint
     * @param ctx
     */
    private void healthCheck(RoutingContext ctx) {
        ctx.response().setStatusCode(200)
                    .setStatusMessage("OK")
                    .putHeader("Content-Type", "text/plain")
                    .end("OK");
    }

    /**
     * Create and configure a Static handler for serving static resources like HTML/JS/Images
     * @return
     */
    private StaticHandler configStaticHandler() {
        return StaticHandler
                    .create()
                    .setIndexPage("index.html")
                    .setCachingEnabled(true)
                    .setFilesReadOnly(true)
                    .setDirectoryListing(false);
    }

    /**
     * Create and configure a websocket<->eventbus bridge
     * @return
     */
    private Router sockJsBridge() {
        final SockJSBridgeOptions bridgeOpts = new SockJSBridgeOptions()
                .addInboundPermitted(new PermittedOptions().setAddressRegex(".*"))      // Allow inbound messages to ANY evenbus address
                .addOutboundPermitted(new PermittedOptions().setAddressRegex(".*"));    // Allow outbound messages to ANY eventbus address
        return SockJSHandler.create(vertx).bridge(bridgeOpts);
    }

    /**
     * Create and configure a clustered Session manager for HTTP session data
     * @return
     */
    private SessionHandler clusteredSession() {
        SessionStore store = ClusteredSessionStore.create(vertx);
        return SessionHandler.create(store);
    }

    /**
     * Return information about this pod
     * @param ctx An instance of {@link RoutingContext} containing references to the request and response for this HTTP interaction
     */
    private void podInfoHandler(RoutingContext ctx) {
        Session session = ctx.session();
        JsonObject podInfo = new JsonObject();
        podInfo.put("id", INSTANCE_ID);
        Integer reqCount = (Integer)session.data().getOrDefault(REQUEST_COUNTER, 0);
        session.data().put(REQUEST_COUNTER, reqCount+1);
        podInfo.put(REQUEST_COUNTER, reqCount+1);
        ctx.response()
            .setStatusCode(200)
            .setStatusMessage("OK")
            .putHeader("Content-Type", "application/json")
            .end(podInfo.encodePrettily());
        LOG.info("Pod Info: {}", podInfo);
        requestCounter.incrementAndGet();
    }

    /**
     * Initialize a cluster-wide shared/atomic counter
     * @param server An ignored parameter to make the method signatures match
     * @return An reference to an instance of {@link Counter}
     */
    private Future<Counter> initSharedData(HttpServer server) {
        return vertx.sharedData().getCounter(REQUEST_COUNTER);
    }

    /**
     * Retrieve the current state of the clustered counter and send out a
     * message on the eventbus with the current state of this pod
     * @param t The timestampe of the periodic scheduler
     */
    private void sendPeriodic(Long t) {
        requestCounter
            .get()
            .onSuccess(this::sendStatusWithRequestCount);
    }

    /**
     * Given the result of an Async request for the shared counter value,
     * send a status message on the event bus.
     * @param res A {@link Long} value which is the current count of REST requests to /api/podinfo
     */
    private void sendStatusWithRequestCount(Long requestCount) {
        JsonObject periodicMessage = new JsonObject().put("id", INSTANCE_ID);
                periodicMessage.put(REQUEST_COUNTER, requestCount);
                periodicMessage.put("appname", currentConfig.getString("appname"));
                vertx.eventBus().publish("status", periodicMessage);
    }

    /**
     * Listen for ConfigMap changes via the Kubernetes API and when the configuration
     * changes, load those configuration changes into the current configuration object.
     */
    private void initConfigWatcher(Boolean isRunningInKubernetes) {
        LOG.info("Detected Service Account Token: Attempting to load configuration from Kubernetes API.");

        ConfigStoreOptions configFile = new ConfigStoreOptions()
                                                .setType("file")
                                                .setFormat("json")
                                                .setConfig(new JsonObject().put("path", "./config.json"));
        ConfigStoreOptions kubeConfig = new ConfigStoreOptions()
                                                .setType("configmap")
                                                .setConfig(
                                                    new JsonObject()
                                                            .put("namespace", System.getenv().getOrDefault("KUBERNETES_NAMESPACE", "default"))
                                                            .put("name", "j4kdemo")
                                                );
        ConfigRetrieverOptions retrOpts = new ConfigRetrieverOptions()
                                                .addStore(configFile)
                                                .addStore(kubeConfig);


        ConfigRetriever retriever = ConfigRetriever.create(vertx, retrOpts);
        retriever.listen(this::loadNewConfig);
    }

    /**
     * Given a configuration change, load that into the current configuration
     * @param change An instance of {@link ConfigChange} with the latest configuration from the Kubernetes API.
     */
    private void loadNewConfig(ConfigChange change) {
        LOG.info("Loading new configuration: {}\n", change.getNewConfiguration().encodePrettily());
        this.currentConfig.mergeIn(change.getNewConfiguration());
    }
}
