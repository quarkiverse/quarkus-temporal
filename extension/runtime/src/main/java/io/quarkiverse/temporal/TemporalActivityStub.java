package io.quarkiverse.temporal;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a field or a constructor parameter of a workflow implementation as an activity stub injection point.
 * <p>
 * Workflow implementations are not CDI beans, because dependency injection into workflow instances can
 * lead to non-deterministic behavior during replay. Activity stubs are the exception: they are created with
 * {@code Workflow.newActivityStub(...)} (or {@code Workflow.newLocalActivityStub(...)}) inside the workflow
 * context, exactly as if the stub had been created in a field initializer or in the workflow constructor.
 * <p>
 * The annotated field or parameter type must be an interface annotated with
 * {@link io.temporal.activity.ActivityInterface}.
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
@Target({ FIELD, PARAMETER })
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
}
