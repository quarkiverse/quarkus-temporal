package io.quarkiverse.temporal;

import static io.quarkiverse.temporal.WorkerFactoryRecorder.createQueueName;

import java.util.function.Function;

import jakarta.enterprise.inject.spi.InjectionPoint;

import io.quarkiverse.temporal.config.RetryRuntimeConfig;
import io.quarkiverse.temporal.config.TemporalBuildtimeConfig;
import io.quarkiverse.temporal.config.TemporalRuntimeConfig;
import io.quarkiverse.temporal.config.WorkerRuntimeConfig;
import io.quarkiverse.temporal.config.WorkflowRuntimeConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.RetryOptions;

@Recorder
public class WorkflowStubRecorder {

    /**
     * The runtime configuration for Temporal.
     */
    final RuntimeValue<TemporalRuntimeConfig> runtimeConfig;

    /**
     * The build-time configuration for Temporal.
     */
    final TemporalBuildtimeConfig buildtimeConfig;

    public WorkflowStubRecorder(RuntimeValue<TemporalRuntimeConfig> runtimeConfig, TemporalBuildtimeConfig buildtimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.buildtimeConfig = buildtimeConfig;
    }

    RetryOptions createRetryOptions(RetryRuntimeConfig config) {
        if (config == null) {
            return RetryOptions.getDefaultInstance();
        }

        RetryOptions.Builder builder = RetryOptions.newBuilder()
                .setInitialInterval(config.initialInterval())
                .setDoNotRetry(config.doNotRetry().toArray(new String[0]))
                .setMaximumAttempts(config.setMaximumAttempts())
                .setBackoffCoefficient(config.backoffCoefficient());

        config.maximumInterval().ifPresent(builder::setMaximumInterval);

        return builder.build();
    }

    <T> WorkflowOptions createWorkflowOptions(SyntheticCreationalContext<T> context, String worker, String workflowId) {

        InjectionPoint injectionPoint = context.getInjectedReference(InjectionPoint.class);
        TemporalWorkflowStub annotation = extractAnnotationFromInjectionPoint(injectionPoint);

        WorkerRuntimeConfig workerRuntimeConfig = runtimeConfig.getValue().worker().get(worker);
        WorkflowRuntimeConfig workflowRuntimeConfig = runtimeConfig.getValue().workflow().get(annotation.group());

        WorkflowOptions.Builder options = WorkflowOptions.newBuilder()
                .setRetryOptions(createRetryOptions(workflowRuntimeConfig.retries()))
                .setDisableEagerExecution(workflowRuntimeConfig.disableEagerExecution())
                .setWorkflowTaskTimeout(workflowRuntimeConfig.workflowTaskTimeout())
                .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy
                        .valueOf("WORKFLOW_ID_CONFLICT_POLICY_" + workflowRuntimeConfig.workflowIdConflictPolicy()))
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy
                        .valueOf("WORKFLOW_ID_REUSE_POLICY_" + workflowRuntimeConfig.workflowIdReusePolicy()))
                .setTaskQueue(createQueueName(worker, workerRuntimeConfig));

        workflowRuntimeConfig.cronSchedule().ifPresent(options::setCronSchedule);
        workflowRuntimeConfig.startDelay().ifPresent(options::setStartDelay);
        workflowRuntimeConfig.workflowRunTimeout().ifPresent(options::setWorkflowRunTimeout);
        workflowRuntimeConfig.workflowExecutionTimeout().ifPresent(options::setWorkflowExecutionTimeout);

        if (workflowId != null) {
            options.setWorkflowId(workflowId);
        } else if (!TemporalWorkflowStub.DEFAULT_WORKFLOW_ID.equals(annotation.workflowId())) {
            options.setWorkflowId(annotation.workflowId());
        }

        return options.validateBuildWithDefaults();
    }

    public <T> Function<SyntheticCreationalContext<TemporalInstance<T>>, TemporalInstance<T>> createWorkflowInstance(
            Class<T> workflow, String worker) {
        return context -> workflowId -> context.getInjectedReference(WorkflowClient.class).newWorkflowStub(workflow,
                createWorkflowOptions(context, worker, workflowId));
    }

    public <T> Function<SyntheticCreationalContext<T>, T> createWorkflowStub(Class<T> workflow, String worker) {
        return context -> context.getInjectedReference(WorkflowClient.class).newWorkflowStub(workflow,
                createWorkflowOptions(context, worker, null));
    }

    TemporalWorkflowStub extractAnnotationFromInjectionPoint(InjectionPoint injectionPoint) {
        return (TemporalWorkflowStub) injectionPoint.getQualifiers().stream()
                .filter(x -> x instanceof TemporalWorkflowStub).findFirst().orElseThrow(
                        () -> new IllegalStateException("workflow stub should always be qualified with TemporalWorkflowStub"));
    }
}
