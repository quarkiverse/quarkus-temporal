package fr.lavachequicode.temporal.plugin.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;


@ConfigMapping(prefix = "quarkus.temporal")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface TemporalRuntimeConfig {

}
