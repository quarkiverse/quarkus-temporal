package io.quarkiverse.temporal.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.temporal.connection")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface ConnectionRuntimeConfig {

    /**
     * target string, which can be either a valid NameResolver-compliant URI, or an authority string
     */
    Optional<String> target();

}
