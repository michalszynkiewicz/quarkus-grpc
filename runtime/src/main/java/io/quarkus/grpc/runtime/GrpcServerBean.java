package io.quarkus.grpc.runtime;

import io.grpc.BindableService;
import io.vertx.core.Vertx;
import io.vertx.grpc.VertxServer;
import io.vertx.grpc.VertxServerBuilder;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Destroyed;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@ApplicationScoped
public class GrpcServerBean {

    @Inject Vertx vertx;

    @Inject @Any Instance<BindableService> services;

    private static final Logger LOGGER = Logger.getLogger(GrpcServerBean.class.getName());
    private volatile VertxServer server;

    public void init(GrpcServerConfiguration configuration) {
        // TODO Support scalability model (using a verticle and instance number)

        VertxServerBuilder builder = VertxServerBuilder
                .forAddress(vertx, configuration.host, configuration.port);

        if (configuration.ssl) {
            builder.useSsl(options -> {
                        options.setSsl(true);

                        if (configuration.useAlpn) {
                            options.setUseAlpn(true);
                        }

                        // TODO Configure the key and certs
                    }

            );
        }

        configuration.maxInboundMessageSize.ifPresent(builder::maxInboundMessageSize);
        configuration.handshakeTimeout.ifPresent(d -> builder.handshakeTimeout(d.toMillis(), TimeUnit.MILLISECONDS));

        if (configuration.transportSecurity != null) {
            File cert = configuration.transportSecurity.certificatePath
                    .map(File::new)
                    .orElse(null);
            File key = configuration.transportSecurity.privateKeyPath
                    .map(File::new)
                    .orElse(null);
            if (cert != null || key != null) {
                builder.useTransportSecurity(cert, key);
            }
        }

        if (services.isUnsatisfied()) {
            LOGGER.warn("Unable to find bean exposing the `BindableService` interface - not starting the gRPC server");
            return;
        }

        services.forEach(builder::addService);

        server = builder.build().start(ar -> {
            if (ar.succeeded()) {
                LOGGER.infof("GRPC Server started on %s:%d", configuration.host, configuration.port);
            } else {
                LOGGER.errorf(ar.cause(), "Unable to start GRPC server on %s:%d", configuration.host,
                        configuration.port);
            }
        });
    }

    public void stop(@Observes @Destroyed(ApplicationScoped.class) Object ev) {
        if (server != null) {
            server.shutdownNow();
            server = null;
        }
    }

    public List<BindableService> getServices() {
        if (services.isUnsatisfied()) {
            return Collections.emptyList();
        } else {
            return services.stream().collect(Collectors.toList());
        }
    }

    public VertxServer getGrpcServer() {
        return server;
    }

}
