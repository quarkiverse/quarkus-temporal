package io.quarkiverse.temporal.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.temporal.connection")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface ConnectionRuntimeConfig {

    /**
     * target string, which can be either a valid NameResolver-compliant URI, or an authority string
     */
    @WithDefault("127.0.0.1:7233")
    String target();

    /**
     * Sets option to enable SSL/ TLS/ HTTPS for gRPC
     */
    @WithDefault("false")
    Boolean enableHttps();
}
