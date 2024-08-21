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
     *
     * <p>
     * Minimum supported version: <code>temporalio/auto-setup:1.24.3.0</code>
     */
    @WithDefault("temporalio/auto-setup")
    String image();

    /**
     * Whether to reuse the Temporal Devservice.
     */
    @WithDefault("true")
    boolean reuse();

}
