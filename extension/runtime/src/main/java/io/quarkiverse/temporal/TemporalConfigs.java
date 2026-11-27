package io.quarkiverse.temporal;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.temporal.config.TemporalBuildtimeConfig;
import io.quarkiverse.temporal.config.TemporalRuntimeConfig;
import io.smallrye.config.SmallRyeConfig;

/**
 * Resolves the Temporal configuration mappings at runtime, for code that is not driven by a {@code ServiceRegistrar}
 * service (such as synthetic bean creators).
 */
public final class TemporalConfigs {

    private TemporalConfigs() {
    }

    public static TemporalRuntimeConfig runtime() {
        return ConfigProvider.getConfig().unwrap(SmallRyeConfig.class).getConfigMapping(TemporalRuntimeConfig.class);
    }

    public static TemporalBuildtimeConfig buildtime() {
        return ConfigProvider.getConfig().unwrap(SmallRyeConfig.class).getConfigMapping(TemporalBuildtimeConfig.class);
    }
}
