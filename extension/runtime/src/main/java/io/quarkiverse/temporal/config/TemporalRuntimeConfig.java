package io.quarkiverse.temporal.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.temporal")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface TemporalRuntimeConfig {

    /**
     * A Namespace is a unit of isolation within the Temporal Platform.
     */
    @WithDefault("default")
    String namespace();

    /**
     * Override human readable identity of the worker. Identity is used to identify a worker and is recorded in the workflow
     * history events. For example when a worker gets an activity task the correspondent ActivityTaskStarted event contains the
     * worker identity as a field. Default is whatever (ManagementFactory. getRuntimeMXBean().getName() returns.
     */
    Optional<String> identity();
}
