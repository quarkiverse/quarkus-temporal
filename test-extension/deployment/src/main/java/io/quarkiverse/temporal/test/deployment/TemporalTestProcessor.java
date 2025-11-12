package io.quarkiverse.temporal.test.deployment;

import static io.quarkiverse.temporal.Constants.TEMPORAL_TESTING_CAPABILITY;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.ParameterizedType;

import io.quarkiverse.temporal.deployment.TemporalProcessor;
import io.quarkiverse.temporal.test.TestWorkflowRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CapabilityBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.temporal.client.WorkflowClient;
import io.temporal.common.context.ContextPropagator;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.interceptors.WorkflowClientInterceptor;
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
        capabilityProducer.produce(new CapabilityBuildItem(TEMPORAL_TESTING_CAPABILITY, "temporal"));
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
    SyntheticBeanBuildItem recordWorkflowClient(
            TestWorkflowEnvironmentBuildItem testWorkflowEnvironmentBuildItem,
            TestWorkflowRecorder recorder) {

        return SyntheticBeanBuildItem
                .configure(WorkflowClient.class)
                .scope(ApplicationScoped.class)
                .unremovable()
                .defaultBean()
                .addInjectionPoint(
                        ParameterizedType.create(Instance.class, ClassType.create(WorkflowClientInterceptor.class)),
                        AnnotationInstance.builder(Any.class).build())
                .addInjectionPoint(ParameterizedType.create(Instance.class, ClassType.create(ContextPropagator.class)),
                        AnnotationInstance.builder(Any.class).build())
                .addInjectionPoint(ParameterizedType.create(Instance.class, ClassType.create(DataConverter.class)),
                        AnnotationInstance.builder(Any.class).build())
                .createWith(recorder.createTestWorkflowClient(testWorkflowEnvironmentBuildItem.testWorkflowEnvironment))
                .setRuntimeInit()
                .done();
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
