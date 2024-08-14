package io.quarkiverse.temporal.test.deployment;

import jakarta.inject.Singleton;

import org.jboss.jandex.ClassType;

import io.quarkiverse.temporal.deployment.TemporalProcessor;
import io.quarkiverse.temporal.deployment.WorkflowClientBuildItem;
import io.quarkiverse.temporal.test.TestWorkflowRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CapabilityBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.WorkerFactory;

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

    @BuildStep(onlyIf = TemporalProcessor.EnableMock.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    SyntheticBeanBuildItem produceWorkerFactorySyntheticBean(
            TestWorkflowEnvironmentBuildItem testWorkflowEnvironmentBuildItem,
            TestWorkflowRecorder recorder) {
        return SyntheticBeanBuildItem
                .configure(WorkerFactory.class)
                .scope(Singleton.class)
                .unremovable()
                .defaultBean()
                .addInjectionPoint(ClassType.create(WorkflowClient.class))
                .runtimeValue(recorder.createTestWorkerFactory(testWorkflowEnvironmentBuildItem.testWorkflowEnvironment))
                .setRuntimeInit()
                .done();
    }
}
