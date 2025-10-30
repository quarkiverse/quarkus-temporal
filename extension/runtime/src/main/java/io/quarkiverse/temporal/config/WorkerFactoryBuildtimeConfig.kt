package io.quarkiverse.temporal.config

import io.quarkus.runtime.annotations.ConfigGroup
import io.smallrye.config.WithDefault


@ConfigGroup
interface WorkerFactoryBuildtimeConfig {
    /**
     * Use virtual threads for workflow execution. Default is false.
     */
    @WithDefault("false")
    fun useVirtualWorkflowThreads(): Boolean

    /**
     * Maximum number of threads the worker factory can use to run workflows. Default is 600.
     */
    @WithDefault("600")
    fun maxWorkflowThreadCount(): Int

    /**
     * Number of workflow instances to keep in cache per worker factory. Default is 600.
     */
    @WithDefault("600")
    fun workflowCacheSize(): Int
}