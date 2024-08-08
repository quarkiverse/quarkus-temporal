package fr.lavachequicode.temporal.test.plugin.deployment;

import fr.lavachequicode.temporal.plugin.spi.WorkerFactoryBuildItem;
import fr.lavachequicode.temporal.plugin.spi.WorkflowClientBuildItem;
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
        capabilityProducer.produce(new CapabilityBuildItem("fr.lavachequicode.temporal.test"));
    }


    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void recordWorkflowClient(
            BuildProducer<WorkflowClientBuildItem> workflowClientProducer,
            BuildProducer<WorkerFactoryBuildItem> workerFactoryProducer,
            TestWorkflowRecorder recorder
    ) {
        TestWorkflowEnvironment testWorkflowEnvironment = recorder.createTestWorkflowEnvironment();
        workflowClientProducer.produce(new WorkflowClientBuildItem(recorder.createTestWorkflowClient(testWorkflowEnvironment)));
        workerFactoryProducer.produce(new WorkerFactoryBuildItem(recorder.createTestWorkerFactory(testWorkflowEnvironment)));
    }
}
