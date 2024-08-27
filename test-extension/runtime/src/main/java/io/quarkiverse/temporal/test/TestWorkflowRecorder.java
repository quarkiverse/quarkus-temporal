package io.quarkiverse.temporal.test;

import java.util.function.Function;

import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.WorkerFactory;

@Recorder
public class TestWorkflowRecorder {
    public TestWorkflowEnvironment createTestWorkflowEnvironment() {
        return TestWorkflowEnvironment.newInstance();
    }

    public Function<SyntheticCreationalContext<WorkflowClient>, WorkflowClient> createTestWorkflowClient(
            TestWorkflowEnvironment testWorkflowEnvironment) {
        return context -> testWorkflowEnvironment.getWorkflowClient();
    }

    public RuntimeValue<WorkerFactory> createTestWorkerFactory(TestWorkflowEnvironment testWorkflowEnvironment) {
        return new RuntimeValue<>(testWorkflowEnvironment.getWorkerFactory());
    }
}
