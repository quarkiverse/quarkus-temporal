package io.quarkiverse.temporal.test;

import static io.quarkiverse.temporal.WorkflowStubHandler.createWorkflowOptions;
import static io.quarkiverse.temporal.WorkflowStubHandler.workerParam;
import static io.quarkiverse.temporal.WorkflowStubHandler.workflowParam;

import io.quarkiverse.temporal.TemporalConfigs;
import io.quarkiverse.temporal.TemporalInstance;
import io.quarkiverse.temporal.WorkflowStubHandler;
import io.quarkiverse.temporal.config.TemporalRuntimeConfig;
import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;
import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;

/**
 * Mock-mode counterpart of {@link WorkflowStubHandler}. A {@code @TemporalWorkflowStub}-qualified
 * bean is {@code @Dependent}, so it's (re)created every time it's injected - including into a test
 * class's own fields, which CDI resolves at test-instance construction time, before
 * {@link MockTestWorkflowResetCallback#beforeEach} has a chance to swap in the next test's
 * {@link WorkflowClient} via {@code QuarkusMock}. Building the stub against the CDI-injected
 * {@code WorkflowClient} reference at that point would bind it to the *previous* test's
 * (possibly already-closed) client. Instead, resolve the client directly from
 * {@link MockTestEnvironmentHolder}, which is always prepared one test ahead - see
 * {@link MockTestWorkflowResetCallback} for how.
 */
public final class TestWorkflowStubHandler {

    private TestWorkflowStubHandler() {
    }

    /**
     * Creates a workflow stub bound to the current mock {@link WorkflowClient}.
     */
    public static class StubCreator implements BeanCreator<Object> {

        @Override
        public Object create(SyntheticCreationalContext<Object> context) {
            Class<?> workflow = workflowParam(context);
            String worker = workerParam(context);
            return currentWorkflowClient(context)
                    .newWorkflowStub(workflow, createWorkflowOptions(context, TemporalConfigs.runtime(), worker, null));
        }
    }

    /**
     * Creates a {@link TemporalInstance} building workflow stubs bound to the current mock {@link WorkflowClient}.
     */
    public static class InstanceCreator implements BeanCreator<Object> {

        @Override
        public Object create(SyntheticCreationalContext<Object> context) {
            Class<?> workflow = workflowParam(context);
            String worker = workerParam(context);
            TemporalRuntimeConfig runtimeConfig = TemporalConfigs.runtime();
            return (TemporalInstance<Object>) workflowId -> currentWorkflowClient(context)
                    .newWorkflowStub(workflow, createWorkflowOptions(context, runtimeConfig, worker, workflowId));
        }
    }

    private static WorkflowClient currentWorkflowClient(SyntheticCreationalContext<?> context) {
        // TestWorkflowEnvironment is ApplicationScoped (proxied): merely obtaining the injected
        // reference does not trigger its creation, only invoking a method on it does. That first
        // creation is what seeds MockTestEnvironmentHolder for the very first test, which is then
        // read below (not the value returned here) so later tests - which swap the holder
        // directly ahead of test-instance construction - are picked up too.
        context.getInjectedReference(TestWorkflowEnvironment.class).getWorkflowClient();
        return MockTestEnvironmentHolder.current().getWorkflowClient();
    }
}
