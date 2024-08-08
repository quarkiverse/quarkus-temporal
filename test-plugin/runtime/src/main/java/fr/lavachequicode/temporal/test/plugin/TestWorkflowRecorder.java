package fr.lavachequicode.temporal.test.plugin;

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

    public WorkflowClient createTestWorkflowClient(TestWorkflowEnvironment testWorkflowEnvironment) {
        return testWorkflowEnvironment.getWorkflowClient();
    }

    public RuntimeValue<WorkerFactory> createTestWorkerFactory(TestWorkflowEnvironment testWorkflowEnvironment) {
        return new RuntimeValue<>(testWorkflowEnvironment.getWorkerFactory());
    }
}
