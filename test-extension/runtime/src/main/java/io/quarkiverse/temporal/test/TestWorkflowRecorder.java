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

            TestWorkflowEnvironment environment = TestWorkflowEnvironment.newInstance(options);
            // Seed the holder immediately: the @Dependent WorkerFactory bean (and the
            // first test's MockTestWorkflowResetCallback) both read from it.
            MockTestEnvironmentHolder.set(environment);
            return environment;
        };
    }

    /**
     * Builds the {@link WorkflowClientOptions} used by the mock TestWorkflowEnvironment, while honoring the CDI wiring.
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
        return context -> MockTestEnvironmentHolder.current().getWorkerFactory();
    }
}
