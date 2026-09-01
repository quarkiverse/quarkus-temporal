package io.quarkiverse.temporal.config;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for the Temporal Development Service.
 */
@ConfigMapping(prefix = "quarkus.temporal.devservices")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface TemporalDevServicesConfig {

    /**
     * Whether to start a Temporal development server when no Temporal connection target is configured.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * The container image used to start the Temporal development server.
     */
    @WithDefault("temporalio/temporal:1.8.2")
    String imageName();

    /**
     * Optional fixed host port for Temporal's gRPC endpoint. By default a random available port is used.
     */
    OptionalInt port();

    /**
     * The label value used to identify a shared Temporal Dev Service container.
     */
    @WithDefault("temporal")
    String serviceName();

    /**
     * Whether to discover and use a Temporal Dev Service container started by another application in dev mode.
     */
    @WithDefault("true")
    boolean shared();

    /**
     * Whether a shared dev-mode container should be reusable after the application that started it exits.
     * Requires Testcontainers reuse to be enabled in {@code ~/.testcontainers.properties}.
     */
    @WithDefault("true")
    boolean reuse();

    /**
     * Namespaces to create in the Temporal development server. Existing namespaces are left unchanged.
     */
    Optional<List<String>> namespaces();

    /**
     * The Web UI URL automatically supplied by the Temporal Dev Service for the Dev UI card.
     */
    Optional<String> webUiUrl();
}
