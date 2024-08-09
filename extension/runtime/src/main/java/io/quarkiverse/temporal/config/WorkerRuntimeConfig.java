package io.quarkiverse.temporal.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface WorkerRuntimeConfig {

    /**
     * task queue name worker uses to poll. It uses this name for both workflow and activity task queue polls
     */
    @WithDefault("default")
    String taskQueue();
}
