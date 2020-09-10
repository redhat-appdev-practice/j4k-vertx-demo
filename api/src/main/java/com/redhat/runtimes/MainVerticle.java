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

        router.route().handler(clusteredSession());
        router.get("/podinfo").handler(this::podInfoHandler);

        router.route().handler(configStaticHandler());

        LOG.info("Create EventBus Bridge");
        router.mountSubRouter("/eventbus", sockJsBridge());

        LOG.info("Creating HTTP Server");
        HttpServer server = vertx.createHttpServer()
            .requestHandler(router);

        server.listen(config().getInteger("port", 8080))
            .compose(this::initSharedData)
            .onSuccess(counter -> {
                requestCounter = counter;
                LOG.info("Setting up periodic status messages");
                vertx.setPeriodic(400, this::sendPeriodic);
            })
            .onComplete(res -> startPromise.complete())
            .onFailure(startPromise::fail);
    }

    private StaticHandler configStaticHandler() {
        return StaticHandler
                    .create()
                    .setIndexPage("index.html")
                    .setCachingEnabled(true)
                    .setFilesReadOnly(true)
                    .setDirectoryListing(false);
    }

    private Router sockJsBridge() {
        final SockJSBridgeOptions bridgeOpts = new SockJSBridgeOptions()
                .addInboundPermitted(new PermittedOptions().setAddressRegex(".*"))
                .addOutboundPermitted(new PermittedOptions().setAddressRegex(".*"));
        return SockJSHandler.create(vertx).bridge(bridgeOpts);
    }

    private SessionHandler clusteredSession() {
        SessionStore store = ClusteredSessionStore.create(vertx);
        return SessionHandler.create(store);
    }

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

    private Future<Counter> initSharedData(HttpServer server) {
        return vertx.sharedData().getCounter(REQUEST_COUNTER);
    }

    private void sendPeriodic(Long t) {
        requestCounter.get()
            .onComplete(res -> {
                JsonObject periodicMessage = new JsonObject().put("id", INSTANCE_ID);
                periodicMessage.put(REQUEST_COUNTER, res.result());
                vertx.eventBus().publish("status", periodicMessage);
            });
    }
}
