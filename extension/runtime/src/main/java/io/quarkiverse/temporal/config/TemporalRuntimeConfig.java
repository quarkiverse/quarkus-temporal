package io.quarkiverse.temporal.config;

import static io.quarkiverse.temporal.Constants.DEFAULT_WORKER_NAME;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithName;
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
     * Enable Micrometer, enabled by default if Micrometer capability is detected.
     */
    @WithName("metrics.enabled")
    @WithDefault("true")
    boolean metricsEnabled();

    /**
     * The interval at which we report metrics to the metric registry. Default is 15 seconds.
     */
    @WithName("metrics.report.duration")
    @WithDefault("15s")
    Duration metricsReportInterval();

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
     * Configuration for the worker factory that drives how workflows and activities are executed.
     */
    @ConfigDocSection
    WorkerFactoryBuildtimeConfig workerFactory();

    /**
     * When set blocks shutdown until all tasks are completed or timeout is reached.
     */
    Optional<Duration> terminationTimeout();
}