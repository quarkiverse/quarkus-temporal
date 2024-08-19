package io.quarkiverse.temporal.config;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.configuration.DurationConverter;
import io.smallrye.config.WithConverter;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface RetryRuntimeConfig {
    /**
     * List of application failures types to not retry
     */
    @WithDefault("[]")
    List<String> doNotRetry();

    /**
     * Interval of the first retry. If coefficient is 1.0 then it is used for all retries.
     * Default is 1 second.
     */

    @WithConverter(DurationConverter.class)
    @WithDefault("1s")
    Duration initialInterval();

    /**
     * Coefficient used to calculate the next retry interval. The next retry interval is previous interval multiplied by this
     * coefficient. Must be 1 or larger. Default is 2.0.
     */
    @WithDefault("2.0")
    Double backoffCoefficient();

    /**
     * When exceeded the amount of attempts, stop. Even if expiration time is not reached. Default is unlimited if set to 0.
     */
    @WithDefault("0")
    Integer setMaximumAttempts();

    /**
     * Maximum interval between retries. Exponential backoff leads to interval increase. This value is the cap of the increase.
     * Default is 100x of initial interval. Can't be less than initialInterval
     */
    @WithConverter(DurationConverter.class)
    Optional<Duration> maximumInterval();
}
