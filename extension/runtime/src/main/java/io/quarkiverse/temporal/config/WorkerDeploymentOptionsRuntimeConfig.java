package io.quarkiverse.temporal.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.temporal.common.VersioningBehavior;

@ConfigGroup
public interface WorkerDeploymentOptionsRuntimeConfig {

    /**
     * The deployment name, which groups related workers across versions.
     * If set, {@code buildId} MUST be defined.
     */
    Optional<String> name();

    /**
     * The Build ID identifying a specific release of your worker code within a deployment.
     * If set, {@code name} MUST be defined.
     */
    Optional<String> buildId();

    /**
     * The deployment version, combining a deployment name and a Build ID (format: {@code <name>.<buildId>}).
     * If {@code buildId} and {@code name} are defined, this property will be ignored.
     */
    Optional<String> version();

    /**
     * If true, opts this worker into the Worker Deployment Versioning feature. Requires {@code version} to be set.
     */
    Optional<Boolean> useVersioning();

    /**
     * Configuration of default versioning behavior for workflows registered on the worker.
     */
    Optional<VersioningBehavior> defaultVersioningBehavior();
}
