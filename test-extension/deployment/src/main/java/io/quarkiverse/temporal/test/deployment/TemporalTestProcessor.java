package io.quarkiverse.temporal.test.deployment;

import io.quarkiverse.temporal.deployment.TemporalProcessor;
import io.quarkiverse.temporal.deployment.WorkerFactoryBuildItem;
import io.quarkiverse.temporal.deployment.WorkflowClientBuildItem;
import io.quarkiverse.temporal.test.TestWorkflowRecorder;
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
        capabilityProducer.produce(new CapabilityBuildItem("io.quarkiverse.temporal.test", "temporal"));
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
