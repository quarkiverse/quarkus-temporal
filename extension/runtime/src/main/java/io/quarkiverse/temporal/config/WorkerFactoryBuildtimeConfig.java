package io.quarkiverse.temporal.config;

import java.time.Duration;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

/**
 * Runtime configuration group for Temporal worker factory behavior.
 *
 * This group controls worker execution characteristics and startup resilience behavior
 * (fail-fast startup, bounded retries, and background retry mode).
 */
@ConfigGroup
public interface WorkerFactoryBuildtimeConfig {
    /**
     * Use virtual threads for workflow execution. Default is false.
     */
    @WithDefault("false")
    boolean usingVirtualWorkflowThreads();

    /**
     * Maximum number of threads the worker factory can use to run workflows. Default is 600.
     */
    @WithDefault("600")
    int maxWorkflowThreadCount();

    /**
     * Number of workflow instances to keep in cache per worker factory. Default is 600.
     */
    @WithDefault("600")
    int workflowCacheSize();

    /**
     * Controls whether application startup fails when Temporal workers cannot start
     * (for example, when the Temporal server is unreachable). Set to false to let
     * the application continue booting without started workers.
     */
    @WithDefault("true")
    boolean failOnStartupError();

    /**
     * Maximum number of attempts to start workers during application startup.
     * This is useful when Temporal is temporarily unavailable at boot time.
     * Ignored when startupBackgroundRetryEnabled is true.
     */
    @WithDefault("1")
    int startupMaxAttempts();

    /**
     * Delay between startup retry attempts when worker startup fails.
     */
    @WithDefault("2s")
    Duration startupRetryDelay();

    /**
     * When enabled (and failOnStartupError is false), worker startup retries run in the
     * background and do not block application startup.
     */
    @WithDefault("false")
    boolean startupBackgroundRetryEnabled();
}
