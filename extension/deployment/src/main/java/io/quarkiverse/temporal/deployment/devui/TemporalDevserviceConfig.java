package io.quarkiverse.temporal.deployment.devui;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.temporal.devservice")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface TemporalDevserviceConfig {

    /**
     * Enable the Temporal Devservice.
     */
    @WithDefault("true")
    Boolean enabled();

    /**
     * The image to use for the Temporal Devservice.
     */
    // @WithDefault("temporalio/auto-setup")
    @WithDefault("temporaliotest/auto-setup")
    String image();

    /**
     * The version of the image to use for the Temporal Devservice.
     */
    @WithDefault("latest")
    String version();

    /**
     * Whether to reuse the Temporal Devservice.
     */
    @WithDefault("true")
    Boolean reuse();

}
