package io.quarkiverse.temporal.deployment.devui;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.temporal.ui")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface TemporalUiConfig {

    /**
     * The url of the Temporal UI.
     */
    Optional<String> url();

}
