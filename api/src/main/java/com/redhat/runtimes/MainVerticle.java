package com.redhat.runtimes;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @Override
    public void start(final Promise<Void> startPromise) {

        LOG.info("Creating Router");
        final Router router = Router.router(vertx);

        // Use the clustered session implementation
        router.route().handler(clusteredSession());

        // Create a GET endpoint
        router.get("/podinfo").handler(this::podInfoHandler);

        // Enable all other endpoints to serve static content from src/main/resources/webroot
        router.route().handler(configStaticHandler());

        LOG.info("Create EventBus Bridge");
        router.mountSubRouter("/eventbus", sockJsBridge());

        LOG.info("Creating HTTP Server");
        HttpServer server = vertx.createHttpServer()
            .requestHandler(router);

        // Start the HTTP Server
        server.listen(config().getInteger("port", 8080))
            .compose(this::initSharedData)                  // Initialize a shared data structure in the cluster
            .onSuccess(counter -> {
                requestCounter = counter;
                LOG.info("Setting up periodic status messages");
                vertx.setPeriodic(400, this::sendPeriodic); // Set up a periodic eventbus message broadcast
            })
            .onComplete(res -> startPromise.complete())     // Let Vert.x know our Verticle is running
            .onFailure(startPromise::fail);                 // OR let Vert.x know our Verticle failed
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
        requestCounter.get()
            .onComplete(res -> {
                JsonObject periodicMessage = new JsonObject().put("id", INSTANCE_ID);
                periodicMessage.put(REQUEST_COUNTER, res.result());
                vertx.eventBus().publish("status", periodicMessage);
            });
    }
}
