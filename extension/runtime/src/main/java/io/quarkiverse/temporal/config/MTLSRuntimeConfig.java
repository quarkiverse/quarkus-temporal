package io.quarkiverse.temporal.config;

import java.nio.file.Path;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface MTLSRuntimeConfig {

    /**
     * Path to the client certificate.
     */
    Optional<Path> clientCertPath();

    /**
     * Path to the client key.
     */
    Optional<Path> clientKeyPath();

    /**
     * Password for the client key.
     */
    Optional<String> password();
}
