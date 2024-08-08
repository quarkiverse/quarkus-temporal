package fr.lavachequicode.temporal.plugin.config;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;
import io.smallrye.config.WithUnnamedKey;

@ConfigMapping(prefix = "quarkus.temporal.worker")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface WorkersRuntimeConfig {

    /**
     * Workers.
     */
    @ConfigDocMapKey("worker-name")
    @WithParentName
    @WithDefaults
    @WithUnnamedKey("<default>")
    Map<String, WorkerRuntimeConfig> workers();

}
