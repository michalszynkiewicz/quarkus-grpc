package io.quarkus.grpc.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

import java.nio.file.Path;
import java.util.Optional;

/**
 * A certificate configuration. Either the certificate and key files must be given, or a key store must be given.
 */
@ConfigGroup
public class CertificateConfig {
    /**
     * The file path to a server certificate or certificate chain in PEM format.
     */
    @ConfigItem
    public Optional<Path> file;

    /**
     * The file path to the corresponding certificate private key file in PEM format.
     */
    @ConfigItem
    public Optional<Path> keyFile;

    /**
     * An optional key store which holds the certificate information instead of specifying separate files.
     */
    @ConfigItem
    public Optional<Path> keyStoreFile;

    /**
     * An optional parameter to specify the type of the key store file. If not given, the type is automatically detected
     * based on the file name.
     */
    @ConfigItem
    public Optional<String> keyStoreFileType;

    /**
     * A parameter to specify the password of the key store file. If not given, the default ("password") is used.
     */
    @ConfigItem(defaultValue = "password")
    public String keyStorePassword;

    /**
     * An optional trust store which holds the certificate information of the certificates to trust
     */
    @ConfigItem
    public Optional<Path> trustStoreFile;

    /**
     * An optional parameter to specify type of the trust store file. If not given, the type is automatically detected
     * based on the file name.
     */
    @ConfigItem
    public Optional<String> trustStoreFileType;

    /**
     * A parameter to specify the password of the trust store file.
     */
    @ConfigItem
    public Optional<String> trustStorePassword;
}
