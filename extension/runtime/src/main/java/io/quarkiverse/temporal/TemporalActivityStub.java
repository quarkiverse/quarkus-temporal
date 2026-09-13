package io.quarkiverse.temporal;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.temporal.activity.ActivityCancellationType;

/**
 * Marks a field of a workflow implementation as an activity stub injection point.
 * <p>
 * Workflow implementations are not CDI beans, because dependency injection into workflow instances can
 * lead to non-deterministic behavior during replay. Activity stubs are the exception: they are created with
 * {@code Workflow.newActivityStub(...)} (or {@code Workflow.newLocalActivityStub(...)}) inside the workflow
 * context, exactly as if the stub had been created in a field initializer or in the workflow constructor.
 * <p>
 * The annotated field type must be an interface annotated with {@link io.temporal.activity.ActivityInterface}.
 * The stubs are injected right after the workflow instance is constructed, so they are available to the
 * workflow method and to signal, query and update handlers. Workflows using a
 * {@link io.temporal.workflow.WorkflowInit} constructor are supported, but the stubs are not available inside
 * that constructor.
 *
 * <pre>
 * public class FileProcessingWorkflowImpl implements FileProcessingWorkflow {
 *
 *     &#64;TemporalActivityStub(startToCloseTimeout = "10s")
 *     FileProcessingActivities activities;
 *
 *     ...
 * }
 * </pre>
 *
 * Durations accept the same syntax as Quarkus configuration properties: an ISO-8601 duration ({@code PT10S}),
 * a simplified form ({@code 10s}, {@code 5m}, {@code 1h}), or a plain number of seconds.
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface TemporalActivityStub {

    /**
     * Maximum time of a single activity execution attempt. Either this or {@link #scheduleToCloseTimeout()}
     * must be set, unless the worker registers default activity options.
     */
    String startToCloseTimeout() default "";

    /**
     * Total time that a workflow is willing to wait for the activity to complete, including retries.
     */
    String scheduleToCloseTimeout() default "";

    /**
     * Time that the activity task can stay in the task queue before it is picked up by a worker.
     */
    String scheduleToStartTimeout() default "";

    /**
     * Maximum permitted time between successful worker heartbeats. Ignored for local activities.
     */
    String heartbeatTimeout() default "";

    /**
     * Task queue to use when dispatching the activity. Defaults to the task queue of the workflow.
     * Ignored for local activities.
     */
    String taskQueue() default "";

    /**
     * How the workflow reacts to a cancellation of the activity. Ignored for local activities.
     */
    ActivityCancellationType cancellationType() default ActivityCancellationType.TRY_CANCEL;

    /**
     * Whether to create a local activity stub instead of a regular activity stub.
     */
    boolean local() default false;

    /**
     * Maximum number of attempts. {@code 0} keeps the SDK default (unlimited).
     */
    int retryMaximumAttempts() default 0;

    /**
     * Interval of the first retry.
     */
    String retryInitialInterval() default "";

    /**
     * Maximum interval between retries.
     */
    String retryMaximumInterval() default "";

    /**
     * Coefficient used to calculate the next retry interval. {@code 0} keeps the SDK default.
     */
    double retryBackoffCoefficient() default 0;

    /**
     * Exception types that are not retried. Matches the type and its subtypes, exactly like
     * {@link io.temporal.common.RetryOptions.Builder#setDoNotRetry(String...)}.
     */
    Class<? extends Throwable>[] retryDoNotRetry() default {};
}
