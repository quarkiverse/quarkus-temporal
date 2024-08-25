package io.quarkiverse.temporal.config;

import static io.quarkiverse.temporal.Constants.DEFAULT_WORKER_NAME;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithUnnamedKey;

@ConfigMapping(prefix = "quarkus.temporal")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface TemporalRuntimeConfig {

    /**
     * A Namespace is a unit of isolation within the Temporal Platform.
     */
    @WithDefault("default")
    String namespace();

    /**
     * Override human-readable identity of the worker. Identity is used to identify a worker and is recorded in the workflow
     * history events. For example when a worker gets an activity task the correspondent ActivityTaskStarted event contains the
     * worker identity as a field. Default is whatever (ManagementFactory. getRuntimeMXBean().getName() returns.
     */
    Optional<String> identity();

    /**
     * Connection to the temporal server.
     */
    @ConfigDocSection
    ConnectionRuntimeConfig connection();

    /**
     * Workers Configuration.
     */
    @ConfigDocMapKey("worker-name")
    @WithDefaults
    @WithUnnamedKey(DEFAULT_WORKER_NAME)
    Map<String, WorkerRuntimeConfig> worker();

    /**
     * Workflow Stub Configuration.
     */
    @ConfigDocMapKey("group-name")
    @WithDefaults
    @WithUnnamedKey(DEFAULT_WORKER_NAME)
    Map<String, WorkflowRuntimeConfig> workflow();

    /**
     * Enable OpenTelemetry to forward telemetry traces and spaces.
     */
    @WithDefault("false")
    Boolean enableTelemetry();
}