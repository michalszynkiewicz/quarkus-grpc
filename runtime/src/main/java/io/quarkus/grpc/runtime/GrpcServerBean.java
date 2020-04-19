package io.quarkus.grpc.runtime;

import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.quarkus.grpc.runtime.config.GrpcServerConfiguration;
import io.quarkus.grpc.runtime.config.SslConfig;
import io.quarkus.runtime.ShutdownContext;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.grpc.VertxServer;
import io.vertx.grpc.VertxServerBuilder;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.Prioritized;
import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@ApplicationScoped
public class GrpcServerBean {

    @Inject Vertx vertx;

    @Inject @Any Instance<BindableService> services;

    @Inject @Any Instance<ServerInterceptor> interceptors;

    private static final Logger LOGGER = Logger.getLogger(GrpcServerBean.class.getName());
    private volatile VertxServer server;

    public void init(GrpcServerConfiguration configuration, ShutdownContext shutdown) {
        // TODO Support scalability model (using a verticle and instance number)

        VertxServerBuilder builder = VertxServerBuilder
                .forAddress(vertx, configuration.host, configuration.port);

        builder.useSsl(options -> {
            try {
                applySslOptions(configuration, options);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        configuration.maxInboundMessageSize.ifPresent(builder::maxInboundMessageSize);
        configuration.handshakeTimeout.ifPresent(d -> builder.handshakeTimeout(d.toMillis(), TimeUnit.MILLISECONDS));

        if (configuration.transportSecurity != null) {
            File cert = configuration.transportSecurity.file
                    .map(File::new)
                    .orElse(null);
            File key = configuration.transportSecurity.keyFile
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

        getSortedInterceptors().forEach(builder::intercept);

        LOGGER.infof("Starting GRPC Server on %s:%d  [SSL enabled: %s]...",
                configuration.host, configuration.port, !configuration.plainText);
        server = builder.build().start(ar -> {
            if (ar.succeeded()) {
                LOGGER.infof("GRPC Server started on %s:%d [SSL enabled: %s]",
                        configuration.host, configuration.port, !configuration.plainText);
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

    private List<ServerInterceptor> getSortedInterceptors() {
        if (interceptors.isUnsatisfied()) {
            return Collections.emptyList();
        }

        return interceptors.stream().sorted((si1, si2) -> {
            int p1 = 0;
            int p2 = 0;
            if (si1 instanceof Prioritized) {
                p1 = ((Prioritized) si1).getPriority();
            }
            if (si2 instanceof Prioritized) {
                p2 = ((Prioritized) si2).getPriority();
            }
            if (si1.equals(si2)) {
                return 0;
            }
            return Integer.compare(p1, p2);
        }).collect(Collectors.toList());
    }

    /**
     * Get an {@code HttpServerOptions} for this server configuration, or null if SSL should not be enabled
     */
    private static void applySslOptions(GrpcServerConfiguration config, HttpServerOptions options)
            throws IOException {

        // Disable plain-text is the ssl configuration is set.
        if ((config.ssl.certificate.file.isPresent() || config.ssl.certificate.keyStoreFile.isPresent()) && config.plainText) {
            LOGGER.debug("Disabling gRPC plain-text as the SSL certificate is configured");
            config.plainText = false;
        }

        if (config.plainText) {
            options.setSsl(false);
            return;
        } else {
            options.setSsl(true);
        }

        SslConfig sslConfig = config.ssl;
        final Optional<Path> certFile = sslConfig.certificate.file;
        final Optional<Path> keyFile = sslConfig.certificate.keyFile;
        final Optional<Path> keyStoreFile = sslConfig.certificate.keyStoreFile;
        final String keystorePassword = sslConfig.certificate.keyStorePassword;
        final Optional<Path> trustStoreFile = sslConfig.certificate.trustStoreFile;
        final Optional<String> trustStorePassword = sslConfig.certificate.trustStorePassword;

        options.setUseAlpn(config.alpn);
        if (config.alpn) {
            options.setAlpnVersions(Arrays.asList(HttpVersion.HTTP_2, HttpVersion.HTTP_1_1));
        }

        if (certFile.isPresent() && keyFile.isPresent()) {
            createPemKeyCertOptions(certFile.get(), keyFile.get(), options);
        } else if (keyStoreFile.isPresent()) {
            final Path keyStorePath = keyStoreFile.get();
            final Optional<String> keyStoreFileType = sslConfig.certificate.keyStoreFileType;
            final String type;
            type = keyStoreFileType.map(String::toLowerCase)
                    .orElseGet(() -> findKeystoreFileType(keyStorePath));

            byte[] data = getFileContent(keyStorePath);
            switch (type) {
                case "pkcs12": {
                    PfxOptions o = new PfxOptions()
                            .setPassword(keystorePassword)
                            .setValue(Buffer.buffer(data));
                    options.setPfxKeyCertOptions(o);
                    break;
                }
                case "jks": {
                    JksOptions o = new JksOptions()
                            .setPassword(keystorePassword)
                            .setValue(Buffer.buffer(data));
                    options.setKeyStoreOptions(o);
                    break;
                }
                default:
                    throw new IllegalArgumentException(
                            "Unknown keystore type: " + type + " valid types are jks or pkcs12");
            }

        }

        if (trustStoreFile.isPresent()) {
            if (!trustStorePassword.isPresent()) {
                throw new IllegalArgumentException("No trust store password provided");
            }
            final String type;
            final Optional<String> trustStoreFileType = sslConfig.certificate.trustStoreFileType;
            final Path trustStoreFilePath = trustStoreFile.get();
            type = trustStoreFileType.map(String::toLowerCase)
                    .orElseGet(() -> findKeystoreFileType(trustStoreFilePath));
            createTrustStoreOptions(trustStoreFilePath, trustStorePassword.get(), type, options);
        }

        for (String cipher : sslConfig.cipherSuites.orElse(Collections.emptyList())) {
            options.addEnabledCipherSuite(cipher);
        }

        for (String protocol : sslConfig.protocols) {
            if (!protocol.isEmpty()) {
                options.addEnabledSecureTransportProtocol(protocol);
            }
        }
        options.setClientAuth(sslConfig.clientAuth);
    }

    private static byte[] getFileContent(Path path) throws IOException {
        byte[] data;
        final InputStream resource = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(path.toString());
        if (resource != null) {
            try (InputStream is = resource) {
                data = doRead(is);
            }
        } else {
            try (InputStream is = Files.newInputStream(path)) {
                data = doRead(is);
            }
        }
        return data;
    }

    private static void createPemKeyCertOptions(Path certFile, Path keyFile,
            HttpServerOptions serverOptions) throws IOException {
        final byte[] cert = getFileContent(certFile);
        final byte[] key = getFileContent(keyFile);
        PemKeyCertOptions pemKeyCertOptions = new PemKeyCertOptions()
                .setCertValue(Buffer.buffer(cert))
                .setKeyValue(Buffer.buffer(key));
        serverOptions.setPemKeyCertOptions(pemKeyCertOptions);
    }

    private static void createTrustStoreOptions(Path trustStoreFile, String trustStorePassword,
            String trustStoreFileType, HttpServerOptions serverOptions) throws IOException {
        byte[] data = getFileContent(trustStoreFile);
        switch (trustStoreFileType) {
            case "pkcs12": {
                PfxOptions options = new PfxOptions()
                        .setPassword(trustStorePassword)
                        .setValue(Buffer.buffer(data));
                serverOptions.setPfxTrustOptions(options);
                break;
            }
            case "jks": {
                JksOptions options = new JksOptions()
                        .setPassword(trustStorePassword)
                        .setValue(Buffer.buffer(data));
                serverOptions.setTrustStoreOptions(options);
                break;
            }
            default:
                throw new IllegalArgumentException(
                        "Unknown truststore type: " + trustStoreFileType + " valid types are jks or pkcs12");
        }
    }

    private static String findKeystoreFileType(Path storePath) {
        final String pathName = storePath.toString();
        if (pathName.endsWith(".p12") || pathName.endsWith(".pkcs12") || pathName.endsWith(".pfx")) {
            return "pkcs12";
        } else {
            // assume jks
            return "jks";
        }
    }

    private static byte[] doRead(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int r;
        while ((r = is.read(buf)) > 0) {
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

}
