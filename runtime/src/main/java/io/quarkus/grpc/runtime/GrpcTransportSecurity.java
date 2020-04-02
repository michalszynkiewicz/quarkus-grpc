package io.quarkus.grpc.runtime;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

import java.util.Optional;

@ConfigGroup
public class GrpcTransportSecurity {

    /**
     * The path to the certificate file.
     */
    @ConfigItem Optional<String> certificatePath;

    /**
     * The path to the private key file.
     */
    @ConfigItem Optional<String> privateKeyPath;
}
