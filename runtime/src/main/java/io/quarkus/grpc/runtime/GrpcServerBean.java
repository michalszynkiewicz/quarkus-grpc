package io.quarkus.grpc.runtime;

import io.grpc.BindableService;
import io.quarkus.runtime.ShutdownContext;
import io.vertx.core.Vertx;
import io.vertx.grpc.VertxServer;
import io.vertx.grpc.VertxServerBuilder;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@ApplicationScoped
public class GrpcServerBean {

    @Inject Vertx vertx;

    @Inject @Any Instance<BindableService> services;

    private static final Logger LOGGER = Logger.getLogger(GrpcServerBean.class.getName());
    private volatile VertxServer server;

    public void init(GrpcServerConfiguration configuration, ShutdownContext shutdown) {
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

        services.forEach(bindable -> {
            builder.addService(bindable);
            LOGGER.infof("Registered GRPC service '%s'", bindable.bindService().getServiceDescriptor().getName());
        });

        server = builder.build().start(ar -> {
            if (ar.succeeded()) {
                LOGGER.infof("GRPC Server started on %s:%d", configuration.host, configuration.port);
            } else {
                LOGGER.errorf(ar.cause(), "Unable to start GRPC server on %s:%d", configuration.host,
                        configuration.port);
            }
        });

        shutdown.addLastShutdownTask(() -> {
                    if (server != null) {
                        LOGGER.info("Stopping GRPC server");
                        CountDownLatch latch = new CountDownLatch(1);
                        server.shutdown(ar -> {
                            if (ar.failed()) {
                                LOGGER.errorf(ar.cause(), "Unable to stop the GRPC server gracefully");
                            }
                            latch.countDown();
                        });

                        try {
                            latch.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            LOGGER.error("Unable to stop the GRPC server gracefully after 10 seconds");
                        }

                        server = null;
                    }
                }
        );
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
