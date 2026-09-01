package io.quarkiverse.temporal.test;

import java.util.function.Function;

import io.quarkiverse.temporal.TemporalInstance;
import io.quarkiverse.temporal.WorkflowStubRecorder;
import io.quarkiverse.temporal.config.TemporalBuildtimeConfig;
import io.quarkiverse.temporal.config.TemporalRuntimeConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;

/**
 * Mock-mode counterpart of {@link WorkflowStubRecorder}. A {@code @TemporalWorkflowStub}-qualified
 * bean is {@code @Dependent}, so it's (re)created every time it's injected - including into a test
 * class's own fields, which CDI resolves at test-instance construction time, before
 * {@link MockTestWorkflowResetCallback#beforeEach} has a chance to swap in the next test's
 * {@link WorkflowClient} via {@code QuarkusMock}. Building the stub against the CDI-injected
 * {@code WorkflowClient} reference at that point would bind it to the *previous* test's
 * (possibly already-closed) client. Instead, resolve the client directly from
 * {@link MockTestEnvironmentHolder}, which is always prepared one test ahead - see
 * {@link MockTestWorkflowResetCallback} for how.
 */
@Recorder
public class TestWorkflowStubRecorder {

    private final WorkflowStubRecorder delegate;

    public TestWorkflowStubRecorder(RuntimeValue<TemporalRuntimeConfig> runtimeConfig,
            TemporalBuildtimeConfig buildtimeConfig) {
        this.delegate = new WorkflowStubRecorder(runtimeConfig, buildtimeConfig);
    }

    public <T> Function<SyntheticCreationalContext<TemporalInstance<T>>, TemporalInstance<T>> createWorkflowInstance(
            Class<T> workflow, String worker) {
        return context -> workflowId -> currentWorkflowClient(context)
                .newWorkflowStub(workflow, delegate.createWorkflowOptions(context, worker, workflowId));
    }

    public <T> Function<SyntheticCreationalContext<T>, T> createWorkflowStub(Class<T> workflow, String worker) {
        return context -> currentWorkflowClient(context)
                .newWorkflowStub(workflow, delegate.createWorkflowOptions(context, worker, null));
    }

    private <T> WorkflowClient currentWorkflowClient(SyntheticCreationalContext<T> context) {
        // TestWorkflowEnvironment is ApplicationScoped (proxied): merely obtaining the injected
        // reference does not trigger its creation, only invoking a method on it does. That first
        // creation is what seeds MockTestEnvironmentHolder for the very first test, which is then
        // read below (not the value returned here) so later tests - which swap the holder
        // directly ahead of test-instance construction - are picked up too.
        context.getInjectedReference(TestWorkflowEnvironment.class).getWorkflowClient();
        return MockTestEnvironmentHolder.current().getWorkflowClient();
    }
}
