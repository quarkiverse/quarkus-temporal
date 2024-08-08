package fr.lavachequicode.temporal.test.plugin.deployment;

import fr.lavachequicode.temporal.plugin.deployment.TemporalProcessor;
import fr.lavachequicode.temporal.plugin.deployment.WorkerFactoryBuildItem;
import fr.lavachequicode.temporal.plugin.deployment.WorkflowClientBuildItem;
import fr.lavachequicode.temporal.test.plugin.TestWorkflowRecorder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CapabilityBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.temporal.testing.TestWorkflowEnvironment;

public class TemporalTestProcessor {

    private static final String FEATURE = "temporal-test";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void capabilities(BuildProducer<CapabilityBuildItem> capabilityProducer) {
        capabilityProducer.produce(new CapabilityBuildItem("fr.lavachequicode.temporal.test", "temporal"));
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(onlyIf = TemporalProcessor.EnableMock.class)
    TestWorkflowEnvironmentBuildItem recordTestEnvironment(
            TestWorkflowRecorder recorder) {
        TestWorkflowEnvironment testWorkflowEnvironment = recorder.createTestWorkflowEnvironment();
        return new TestWorkflowEnvironmentBuildItem(testWorkflowEnvironment);
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(onlyIf = TemporalProcessor.EnableMock.class)
    WorkflowClientBuildItem recordWorkflowClient(
            TestWorkflowEnvironmentBuildItem testWorkflowEnvironmentBuildItem,
            TestWorkflowRecorder recorder) {
        return new WorkflowClientBuildItem(
                recorder.createTestWorkflowClient(testWorkflowEnvironmentBuildItem.testWorkflowEnvironment));

    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(onlyIf = TemporalProcessor.EnableMock.class)
    WorkerFactoryBuildItem recordWorkerFactory(
            TestWorkflowEnvironmentBuildItem testWorkflowEnvironmentBuildItem,
            TestWorkflowRecorder recorder) {
        return new WorkerFactoryBuildItem(
                recorder.createTestWorkerFactory(testWorkflowEnvironmentBuildItem.testWorkflowEnvironment));
    }
}
