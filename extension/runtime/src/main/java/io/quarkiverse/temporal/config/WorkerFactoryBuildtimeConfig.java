package io.quarkiverse.temporal.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface WorkerFactoryBuildtimeConfig {
    /**
     * Use virtual threads for workflow execution. Default is false.
     */
    @WithDefault("false")
    boolean useVirtualWorkflowThreads();

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
}