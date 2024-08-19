package io.quarkiverse.temporal.config;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.grpc.Status;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface RpcRetryRuntimeConfig {

    /**
     * Interval of the first retry, on regular failures. If coefficient is 1.0 then it is used for all retries.
     * Defaults to 100ms.
     */
    @WithDefault("100ms")
    Duration initialInterval();

    /**
     * Interval of the first retry, on congestion related failures (i. e. RESOURCE_EXHAUSTED errors). If coefficient is 1.0 then
     * it is used for all retries.
     * Defaults to 1000ms.
     */
    @WithDefault("1000ms")
    Duration congestionInitialInterval();

    /**
     * Maximum time to retry. When exceeded the retries stop even if maximum retries is not reached yet.
     * Defaults to 1 minute.
     */
    @WithDefault("1m")
    Duration expiration();

    /**
     * Coefficient used to calculate the next retry interval. The next retry interval is previous interval multiplied by this
     * coefficient. Must be 1 or larger.
     * Default is 1.5.
     */
    @WithDefault("1.5")
    Double backoffCoefficient();

    /**
     * When exceeded the amount of attempts, stop. Even if expiration time is not reached.
     * Default is unlimited which is chosen if set to 0.
     */
    @WithDefault("0")
    Integer maximumAttempts();

    /**
     * Maximum interval between retries. Exponential backoff leads to interval increase. This value is the cap of the increase.
     * Default is 50x of initial interval. Can't be less than initial-interval
     */
    Optional<Duration> maximumInterval();

    /**
     * Maximum amount of jitter to apply. 0.2 means that actual retry time can be +/- 20% of the calculated time. Set to 0 to
     * disable jitter. Must be lower than 1.
     * Default is 0.2.
     */
    @WithDefault("0.2")
    Double maximumJitterCoefficient();

    /**
     * Makes request that receives a server response with gRPC code and failure of detailsClass type non-retryable.
     */
    Optional<List<Status.Code>> doNotRetry();
}
