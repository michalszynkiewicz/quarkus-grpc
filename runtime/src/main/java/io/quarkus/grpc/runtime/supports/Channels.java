package io.quarkus.grpc.runtime.supports;

import io.grpc.Channel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.grpc.runtime.annotations.GrpcService;
import io.quarkus.grpc.runtime.annotations.GrpcServiceLiteral;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.enterprise.inject.Instance;
import javax.net.ssl.SSLException;
import java.io.File;

public class Channels {

    private Channels() {
        // Avoid direct instantiation
    }

    public static Channel createChannel(String prefix) throws SSLException {
        Config config = ConfigProvider.getConfig();
        String host = getMandatoryProperty(config, prefix, "host", String.class);
        int port = getOptionalProperty(config, prefix, "port", Integer.class,9000);
        boolean defaultPlainText = getOptionalProperty(config, prefix,"ssl.trust-store", String.class, null) == null;
        boolean plainText = getOptionalProperty(config, prefix, "plain-text", Boolean.class, defaultPlainText);

        SslContext context = null;
        if (!plainText) {
            String trustStorePath = getOptionalProperty(config, prefix, "ssl.trust-store", String.class, null);
            String certificatePath = getOptionalProperty(config, prefix, "ssl.certificate", String.class, null);
            String keyPath = getOptionalProperty(config, prefix, "ssl.key", String.class, null);
            SslContextBuilder sslContextBuilder = GrpcSslContexts.forClient();
            if (trustStorePath != null) {
                sslContextBuilder.trustManager(new File(trustStorePath));
            }

            if (certificatePath != null) {
                    sslContextBuilder.keyManager(new File(certificatePath), new File(keyPath));
            }

            context = sslContextBuilder.build();
        }

        NettyChannelBuilder builder = NettyChannelBuilder.forAddress(host, port);
        if (plainText) {
            builder.usePlaintext();
        }
        if (context != null) {
            builder.sslContext(context);
        }
        return builder.build();
    }

    public static Channel retrieveChannel(String name) {
        InstanceHandle<Channel> instance = Arc.container().instance(Channel.class, GrpcServiceLiteral.of(name));
        if (! instance.isAvailable()) {
            throw new IllegalStateException("Unable to retrieve the GRPC Channel " + name);
        }
        return instance.get();
    }

    private static <T> T getMandatoryProperty(Config config, String prefix, String attr, Class<T> type) {
        return config.getValue(prefix + attr, type);
    }

    private static <T> T getOptionalProperty(Config config, String prefix, String attr, Class<T> type, T defaultValue) {
        return config.getOptionalValue(prefix + attr, type).orElse(defaultValue);
    }

}
