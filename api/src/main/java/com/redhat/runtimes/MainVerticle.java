package com.redhat.runtimes;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    String uniqueInstanceId = UUID.randomUUID().toString();

    @Override
    public void start(final Promise<Void> startPromise) {
        
        vertx.setPeriodic(200, t -> {
            vertx.eventBus().publish("status", new JsonObject().put("id", uniqueInstanceId));
        });

        final SockJSBridgeOptions bridgeOpts = new SockJSBridgeOptions()
                .addInboundPermitted(new PermittedOptions().setAddressRegex(".*"))
                .addOutboundPermitted(new PermittedOptions().setAddressRegex(".*"));

        final Router router = Router.router(vertx);
        CorsHandler corsHandler = CorsHandler.create()
                                            .allowCredentials(true)
                                            .addOrigin(config().getString("origin", "http://localhost:8080"));
        router.route().handler(corsHandler);

        router.mountSubRouter("/eventbus", SockJSHandler.create(vertx).bridge(bridgeOpts));

        router.route().handler(StaticHandler.create());

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(config().getInteger("port", 80))
            .onComplete(server -> startPromise.complete())
            .onFailure(startPromise::fail);
    }
}
