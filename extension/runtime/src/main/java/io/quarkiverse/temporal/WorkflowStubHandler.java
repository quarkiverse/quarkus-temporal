package io.quarkiverse.temporal;

import static io.quarkiverse.temporal.WorkerFactoryHandler.createQueueName;

import jakarta.enterprise.inject.spi.InjectionPoint;

import io.quarkiverse.temporal.config.RetryRuntimeConfig;
import io.quarkiverse.temporal.config.TemporalRuntimeConfig;
import io.quarkiverse.temporal.config.WorkerRuntimeConfig;
import io.quarkiverse.temporal.config.WorkflowRuntimeConfig;
import io.quarkiverse.temporal.config.WorkflowRuntimeConfig.TemporalWorkflowIdConflictPolicy;
import io.quarkiverse.temporal.config.WorkflowRuntimeConfig.TemporalWorkflowIdReusePolicy;
import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.RetryOptions;

/**
 * Creates the {@code @TemporalWorkflowStub}-qualified synthetic beans: plain workflow stubs ({@link StubCreator}) and
 * {@link TemporalInstance} factories ({@link InstanceCreator}).
 * <p>
 * Both creators read the {@value #PARAM_WORKFLOW} (workflow interface class) and {@value #PARAM_WORKER} (worker name)
 * bean parameters.
 */
public final class WorkflowStubHandler {

    public static final String PARAM_WORKFLOW = "workflow";
    public static final String PARAM_WORKER = "worker";

    private WorkflowStubHandler() {
    }

    /**
     * Creates a workflow stub bound to the {@link WorkflowClient} bean.
     */
    public static class StubCreator implements BeanCreator<Object> {

        @Override
        public Object create(SyntheticCreationalContext<Object> context) {
            Class<?> workflow = workflowParam(context);
            String worker = workerParam(context);
            return context.getInjectedReference(WorkflowClient.class).newWorkflowStub(workflow,
                    createWorkflowOptions(context, TemporalConfigs.runtime(), worker, null));
        }
    }

    /**
     * Creates a {@link TemporalInstance} that builds workflow stubs bound to the {@link WorkflowClient} bean.
     */
    public static class InstanceCreator implements BeanCreator<Object> {

        @Override
        public Object create(SyntheticCreationalContext<Object> context) {
            Class<?> workflow = workflowParam(context);
            String worker = workerParam(context);
            TemporalRuntimeConfig runtimeConfig = TemporalConfigs.runtime();
            WorkflowClient workflowClient = context.getInjectedReference(WorkflowClient.class);
            return (TemporalInstance<Object>) workflowId -> workflowClient.newWorkflowStub(workflow,
                    createWorkflowOptions(context, runtimeConfig, worker, workflowId));
        }
    }

    public static Class<?> workflowParam(SyntheticCreationalContext<?> context) {
        return (Class<?>) context.getParams().get(PARAM_WORKFLOW);
    }

    public static String workerParam(SyntheticCreationalContext<?> context) {
        return (String) context.getParams().get(PARAM_WORKER);
    }

    static RetryOptions createRetryOptions(RetryRuntimeConfig config) {
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

    public static WorkflowOptions createWorkflowOptions(SyntheticCreationalContext<?> context,
            TemporalRuntimeConfig runtimeConfig, String worker, String workflowId) {

        InjectionPoint injectionPoint = context.getInjectedReference(InjectionPoint.class);
        TemporalWorkflowStub annotation = extractAnnotationFromInjectionPoint(injectionPoint);

        WorkerRuntimeConfig workerRuntimeConfig = runtimeConfig.worker().get(worker);
        WorkflowRuntimeConfig workflowRuntimeConfig = runtimeConfig.workflow().get(annotation.group());

        TemporalWorkflowIdReusePolicy reusePolicy = workflowRuntimeConfig.workflowIdReusePolicy();
        TemporalWorkflowIdConflictPolicy conflictPolicy = workflowRuntimeConfig.workflowIdConflictPolicy();
        validateWorkflowIdPolicies(annotation.group(), reusePolicy, conflictPolicy);

        WorkflowOptions.Builder options = WorkflowOptions.newBuilder()
                .setRetryOptions(createRetryOptions(workflowRuntimeConfig.retries()))
                .setDisableEagerExecution(workflowRuntimeConfig.disableEagerExecution())
                .setWorkflowTaskTimeout(workflowRuntimeConfig.workflowTaskTimeout())
                .setTaskQueue(createQueueName(worker, workerRuntimeConfig));

        // an UNSPECIFIED policy is left unset so that the policy is not sent to the server, which then applies its own
        // default. Sending an explicit conflict policy also prevents the server from migrating a TERMINATE_IF_RUNNING
        // reuse policy, which it only does when the conflict policy is unspecified.
        if (reusePolicy != TemporalWorkflowIdReusePolicy.UNSPECIFIED) {
            options.setWorkflowIdReusePolicy(WorkflowIdReusePolicy.valueOf("WORKFLOW_ID_REUSE_POLICY_" + reusePolicy));
        }
        if (conflictPolicy != TemporalWorkflowIdConflictPolicy.UNSPECIFIED) {
            options.setWorkflowIdConflictPolicy(
                    WorkflowIdConflictPolicy.valueOf("WORKFLOW_ID_CONFLICT_POLICY_" + conflictPolicy));
        }

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

    /**
     * Rejects the workflow id policy combinations that the Temporal server refuses with an InvalidArgument error, so that a
     * misconfiguration surfaces as a readable error instead of a gRPC failure on the first workflow start.
     */
    static void validateWorkflowIdPolicies(String group, TemporalWorkflowIdReusePolicy reusePolicy,
            TemporalWorkflowIdConflictPolicy conflictPolicy) {
        if (conflictPolicy == TemporalWorkflowIdConflictPolicy.UNSPECIFIED) {
            return;
        }
        if (reusePolicy == TemporalWorkflowIdReusePolicy.TERMINATE_IF_RUNNING) {
            throw new ConfigurationException(configPropertyName(group, "workflow-id-reuse-policy")
                    + "=TERMINATE_IF_RUNNING cannot be used together with "
                    + configPropertyName(group, "workflow-id-conflict-policy") + "=" + conflictPolicy
                    + ". Use workflow-id-conflict-policy=TERMINATE_EXISTING instead, or leave the conflict policy unset.");
        }
        if (reusePolicy == TemporalWorkflowIdReusePolicy.REJECT_DUPLICATE
                && conflictPolicy == TemporalWorkflowIdConflictPolicy.TERMINATE_EXISTING) {
            throw new ConfigurationException(configPropertyName(group, "workflow-id-reuse-policy")
                    + "=REJECT_DUPLICATE cannot be used together with "
                    + configPropertyName(group, "workflow-id-conflict-policy") + "=TERMINATE_EXISTING.");
        }
    }

    static String configPropertyName(String group, String property) {
        return Constants.DEFAULT_WORKFLOW_GROUP_NAME.equals(group)
                ? "quarkus.temporal.workflow." + property
                : "quarkus.temporal.workflow." + group + "." + property;
    }

    static TemporalWorkflowStub extractAnnotationFromInjectionPoint(InjectionPoint injectionPoint) {
        return (TemporalWorkflowStub) injectionPoint.getQualifiers().stream()
                .filter(x -> x instanceof TemporalWorkflowStub).findFirst().orElseThrow(
                        () -> new IllegalStateException("workflow stub should always be qualified with TemporalWorkflowStub"));
    }
}
