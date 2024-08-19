package io.quarkiverse.temporal.config;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.configuration.DurationConverter;
import io.smallrye.config.WithConverter;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface WorkflowRuntimeConfig {

    enum TemporalWorkflowIdReusePolicy {
        UNSPECIFIED,
        ALLOW_DUPLICATE,
        ALLOW_DUPLICATE_FAILED_ONLY,
        REJECT_DUPLICATE,
        TERMINATE_IF_RUNNING
    }

    enum TemporalWorkflowIdConflictPolicy {
        UNSPECIFIED,
        FAIL,
        USE_EXISTING,
        TERMINATE_EXISTING,
    }

    /**
     * Specifies server behavior if a completed workflow with the same id exists. Note that under no conditions Temporal allows
     * two workflows with the same namespace and workflow id run simultaneously. See @line setWorkflowIdConflictPolicy for
     * handling a workflow id duplication with a Running workflow.
     * Default value if not set: AllowDuplicate
     */
    @WithDefault("ALLOW_DUPLICATE")
    TemporalWorkflowIdReusePolicy workflowIdReusePolicy();

    /**
     * Specifies server behavior if a Running workflow with the same id exists. See setWorkflowIdReusePolicy for handling a
     * workflow id duplication with a Closed workflow. Cannot be set when workflow-id-reuse-policy is WorkflowIdReusePolicy.
     * Default value if not set: Fail
     */
    @WithDefault("FAIL")
    TemporalWorkflowIdConflictPolicy workflowIdConflictPolicy();

    /**
     * The time after which a workflow run is automatically terminated by Temporal service with WORKFLOW_EXECUTION_TIMED_OUT
     * status.
     * The default is set to the same value as the Workflow Execution Timeout.
     */
    @WithConverter(DurationConverter.class)
    Optional<Duration> workflowRunTimeout();

    /**
     * The time after which workflow execution (which includes run retries and continue as new) is automatically terminated by
     * Temporal service with WORKFLOW_EXECUTION_TIMED_OUT status.
     * The default value is ∞ (infinite) - [TO DO]: check with temporal how to set this infinite value
     */
    @WithConverter(DurationConverter.class)
    Optional<Duration> workflowExecutionTimeout();

    /**
     * Maximum execution time of a single Workflow Task. In the majority of cases there is no need to change this timeout. Note
     * that this timeout is not related to the overall Workflow duration in any way. It defines for how long the Workflow can
     * get blocked in the case of a Workflow Worker crash.
     * The default value is 10 seconds. Maximum value allowed by the Temporal Server is 1 minute.
     */
    @WithDefault("10s")
    @WithConverter(DurationConverter.class)
    Duration workflowTaskTimeout();

    /**
     * Retry options
     */
    @ConfigDocSection
    RetryRuntimeConfig retries();

    /**
     * cron schedule
     */
    Optional<String> cronSchedule();

    /**
     * If WorkflowClient is used to create a WorkerFactory that is
     * started
     * has a non-paused worker on the right task queue
     * has available workflow task executor slots
     * and such a WorkflowClient is used to start a workflow, then the first workflow task could be dispatched on this local
     * worker with the response to the start call if Server supports it. This option can be used to disable this mechanism.
     * Default is true
     */
    @WithDefault("true")
    Boolean disableEagerExecution();

    /**
     * Time to wait before dispatching the first workflow task. If the workflow gets a signal before the delay, a workflow task
     * will be dispatched and the rest of the delay will be ignored. A signal from signal with start will not trigger a workflow
     * task. Cannot be set the same time as a CronSchedule.
     */
    @WithConverter(DurationConverter.class)
    Optional<Duration> startDelay();
}
