package io.quarkus.grpc.runtime;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public class GrpcServerConfiguration {

    /**
     * The gRPC Server port.
     */
    @ConfigItem(defaultValue = "9000")
    int port;

    /**
     * The gRPC server host.
     */
    @ConfigItem(defaultValue = "0.0.0.0")
    String host;

    /**
     * The gRPC handshake timeout.
     */
    @ConfigItem
    Optional<Duration> handshakeTimeout;

    /**
     * The max inbound message size in bytes.
     */
    @ConfigItem OptionalInt maxInboundMessageSize;

    /**
     * Whether or not SSL should be used.
     */
    @ConfigItem(defaultValue = "false") boolean ssl;

    /**
     * Whether ALPN should be used.
     */
    @ConfigItem(defaultValue = "true")
    public boolean useAlpn;

    // TODO When SSL is configured, we should allow configuring the various SSL aspect.

    /**
     * Configures the transport security.
     */
    @ConfigItem
    public GrpcTransportSecurity transportSecurity;

    /**
     * Configures the path to the keystore (JKS file).
     */
    @ConfigItem
    public Optional<String> keystorePath;

    /**
     * Configures the keystore password.
     */
    @ConfigItem
    public Optional<String> keystorePassword;

}
