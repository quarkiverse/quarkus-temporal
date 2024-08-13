package io.quarkiverse.temporal.config;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.configuration.DurationConverter;
import io.smallrye.config.WithConverter;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface WorkerRuntimeConfig {

    /**
     * Task queue name worker uses to poll. It uses this name for both workflow and activity task queue polls.
     */
    @WithDefault("default")
    String taskQueue();

    /**
     * Maximum number of activities started per second by this worker. Default is 0 which means unlimited.
     */
    @WithDefault("0")
    Double maxWorkerActivitiesPerSecond();

    /**
     * Maximum number of activities executed in parallel. Default is 200, which is chosen if set to zero.
     */
    @WithDefault("200")
    Integer maxConcurrentActivityExecutionSize();

    /**
     * Maximum number of simultaneously executed workflow tasks. Default is 200, which is chosen if set to zero.
     */
    @WithDefault("200")
    Integer maxConcurrentWorkflowTaskExecutionSize();

    /**
     * Maximum number of local activities executed in parallel. Default is 200, which is chosen if set to zero.
     */
    @WithDefault("200")
    Integer maxConcurrentLocalActivityExecutionSize();

    /**
     * Sets the rate limiting on number of activities that can be executed per second. This is managed by the server and
     * controls activities per second for the entire task queue across all the workers. Notice that the number is represented in
     * double, so that you can set it to less than 1 if needed. For example, set the number to 0.1 means you want your activity
     * to be executed once every 10 seconds. This can be used to protect down stream services from flooding. The zero value of
     * these uses the default value. Default is unlimited.
     */
    @WithDefault("0")
    Double maxTaskQueueActivitiesPerSecond();

    /**
     * Sets the maximum number of simultaneous long poll requests to the Temporal Server to retrieve workflow tasks. Changing
     * this value will affect the rate at which the worker is able to consume tasks from a task queue.
     * Due to internal logic where pollers alternate between sticky and non-sticky queues, this value cannot be 1 and will be
     * adjusted to 2 if set to that value.
     * Default is 5, which is chosen if set to zero.
     */
    @WithDefault("5")
    Integer maxConcurrentWorkflowTaskPollers();

    /**
     * Number of simultaneous poll requests on activity task queue. Consider incrementing if the worker is not throttled due to
     * `MaxActivitiesPerSecond` or `MaxConcurrentActivityExecutionSize` options and still cannot keep up with the request rate.
     * Default is 5, which is chosen if set to zero.
     */
    @WithDefault("5")
    Integer maxConcurrentActivityTaskPollers();

    /**
     * If set to true worker would only handle workflow tasks and local activities. Non-local activities will not be executed by
     * this worker.
     * Default is false.
     */
    @WithDefault("false")
    Boolean localActivityWorkerOnly();

    /**
     * Time period in ms that will be used to detect workflows deadlock. Default is 1000ms, which is chosen if set to zero.
     * Specifies an amount of time in milliseconds that workflow tasks are allowed to execute without interruption. If workflow
     * task runs longer than specified interval without yielding (like calling an Activity), it will fail automatically.
     */
    @WithDefault("1000")
    Long defaultDeadlockDetectionTimeout();

    /**
     * The maximum amount of time between sending each pending heartbeat to the server. Regardless of heartbeat timeout, no
     * pending heartbeat will wait longer than this amount of time to send. Default is 60s, which is chosen if set to null or 0.
     */
    @WithDefault("60s")
    @WithConverter(DurationConverter.class)
    Duration maxHeartbeatThrottleInterval();

    /**
     * The default amount of time between sending each pending heartbeat to the server. This is used if the ActivityOptions do
     * not provide a HeartbeatTimeout. Otherwise, the interval becomes a value a bit smaller than the given HeartbeatTimeout.
     * Default is 30s, which is chosen if set to null or 0.
     */
    @WithDefault("30s")
    @WithConverter(DurationConverter.class)
    Duration defaultHeartbeatThrottleInterval();

    /**
     * Timeout for a workflow task routed to the "sticky worker" - host that has the workflow instance cached in memory. Once it
     * times out, then it can be picked up by any worker.
     * Default value is 5 seconds.
     */
    @WithDefault("5s")
    @WithConverter(DurationConverter.class)
    Duration stickyQueueScheduleToStartTimeout();

    /**
     * Disable eager activities. If set to true, eager execution will not be requested for activities requested from workflows
     * bound to this Worker.
     * Eager activity execution means the server returns requested eager activities directly from the workflow task back to this
     * worker which is faster than non-eager which may be dispatched to a separate worker.
     * Defaults to false, meaning that eager activity execution is permitted
     */
    @WithDefault("false")
    Boolean disableEagerExecution();

    /**
     * Opts the worker in to the Build-ID-based versioning feature. This ensures that the worker will only receive tasks which
     * it is compatible with. For more information see: TODO: Doc link
     * Defaults to false
     */
    @WithDefault("false")
    Boolean useBuildIdForVersioning();

    /**
     * Set a unique identifier for this worker. The identifier should be stable with respect to the code the worker uses for
     * workflows, activities, and interceptors. For more information see: TODO: Doc link
     * A Build Id must be set if useBuildIdForVersioning is set true.
     */
    Optional<String> buildId();

    /**
     * During graceful shutdown, as when calling WorkerFactory. shutdown(), if the workflow cache is enabled, this timeout
     * controls how long to wait for the sticky task queue to drain before shutting down the worker. If set the worker will stop
     * making new poll requests on the normal task queue, but will continue to poll the sticky task queue until the timeout is
     * reached. This value should always be greater than clients rpc long poll timeout, which can be set via
     * WorkflowServiceStubsOptions. Builder. setRpcLongPollTimeout(Duration).
     * Default is not to wait.
     */
    @WithDefault("0s")
    @WithConverter(DurationConverter.class)
    Duration stickyTaskQueueDrainTimeout();

    /**
     * Override identity of the worker primary specified in a WorkflowClient options.
     */
    Optional<String> identity();
}