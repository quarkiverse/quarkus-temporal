package io.quarkiverse.temporal.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithName;
import io.smallrye.config.WithUnnamedKey;

@ConfigMapping(prefix = "quarkus.temporal")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface TemporalBuildtimeConfig {

    /**
     * enable mock for testing
     */
    @WithDefault("false")
    Boolean enableMock();

    /**
     * If Temporal registers in the health check by pinging the service.
     */
    @WithName("health.enabled")
    @WithDefault("true")
    boolean healthEnabled();

    /**
     * enable mock for testing
     */
    @WithDefault("true")
    Boolean startWorkers();

    /**
     * Workers Configuration.
     */
    @ConfigDocMapKey("worker-name")
    @WithDefaults
    @WithUnnamedKey("<default>")
    Map<String, WorkerBuildtimeConfig> worker();

    /**
     * Explicitly bind context propagators to the workflow client.
     */
    Optional<List<String>> contextPropagatorClasses();
}