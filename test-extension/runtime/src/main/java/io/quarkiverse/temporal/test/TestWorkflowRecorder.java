package io.quarkiverse.temporal.test;

import java.util.Optional;
import java.util.function.Function;

import io.quarkiverse.temporal.WorkflowClientOptionsSupport;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.testing.TestEnvironmentOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.WorkerFactory;

@Recorder
public class TestWorkflowRecorder {
    public Function<SyntheticCreationalContext<TestWorkflowEnvironment>, TestWorkflowEnvironment> createTestWorkflowEnvironment() {
        return context -> {
            TestEnvironmentOptions options = TestEnvironmentOptions.newBuilder()
                    .setWorkflowClientOptions(createTestWorkflowClientOptions(context))
                    .build();

            return TestWorkflowEnvironment.newInstance(options);
        };
    }

    /**
     * Builds the {@link WorkflowClientOptions} used by the mock TestWorkflowEnvironment.
     * This honors CDI wiring similarly to the runtime recorder.
     */
    public WorkflowClientOptions createTestWorkflowClientOptions(SyntheticCreationalContext<?> context) {
        return WorkflowClientOptionsSupport.buildFromContext(
                context,
                "default",
                Optional.empty());
    }

    public Function<SyntheticCreationalContext<WorkflowClient>, WorkflowClient> createTestWorkflowClient() {
        return context -> {
            TestWorkflowEnvironment testWorkflowEnvironment = context.getInjectedReference(TestWorkflowEnvironment.class);
            return testWorkflowEnvironment.getWorkflowClient();
        };
    }

    public Function<SyntheticCreationalContext<WorkerFactory>, WorkerFactory> createTestWorkerFactory() {
        return context -> {
            TestWorkflowEnvironment testWorkflowEnvironment = context.getInjectedReference(TestWorkflowEnvironment.class);
            return testWorkflowEnvironment.getWorkerFactory();
        };
    }
}
