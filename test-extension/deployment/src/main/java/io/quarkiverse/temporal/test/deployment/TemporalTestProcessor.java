package io.quarkiverse.temporal.test.deployment;

import static io.quarkiverse.temporal.Constants.TEMPORAL_TESTING_CAPABILITY;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;

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
    SyntheticBeanBuildItem recordTestEnvironment(TestWorkflowRecorder recorder) {
        return SyntheticBeanBuildItem
                .configure(TestWorkflowEnvironment.class)
                .scope(ApplicationScoped.class)
                .unremovable()
                .addInjectionPoint(
                        ParameterizedType.create(Instance.class, ClassType.create(WorkflowClientInterceptor.class)),
                        AnnotationInstance.builder(Any.class).build())
                .addInjectionPoint(
                        ParameterizedType.create(Instance.class, ClassType.create(ContextPropagator.class)),
                        AnnotationInstance.builder(Any.class).build())
                .addInjectionPoint(
                        ParameterizedType.create(Instance.class, ClassType.create(DataConverter.class)),
                        AnnotationInstance.builder(Any.class).build())
                .createWith(recorder.createTestWorkflowEnvironment())
                .setRuntimeInit()
                .done();
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(onlyIf = TemporalProcessor.EnableMock.class)
    SyntheticBeanBuildItem recordWorkflowClient(TestWorkflowRecorder recorder) {

        return SyntheticBeanBuildItem
                .configure(WorkflowClient.class)
                .scope(ApplicationScoped.class)
                .unremovable()
                .defaultBean()
                .addInjectionPoint(ClassType.create(TestWorkflowEnvironment.class))
                .createWith(recorder.createTestWorkflowClient())
                .setRuntimeInit()
                .done();
    }

    @BuildStep(onlyIf = TemporalProcessor.EnableMock.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    SyntheticBeanBuildItem produceWorkerFactorySyntheticBean(TestWorkflowRecorder recorder) {
        // @Dependent (not a normal scope): io.temporal.worker.WorkerFactory is a final class
        // with only a private constructor and cannot be CDI-proxied. A fresh instance is
        // instead resolved from MockTestEnvironmentHolder at every injection point - see
        // MockTestEnvironmentHolder / MockTestWorkflowResetCallback for how freshness is
        // still achieved per test.
        // The TestWorkflowEnvironment injection point below is not read directly by
        // createTestWorkerFactory() any more, but it must stay: declaring it is what forces
        // ArC to create the TestWorkflowEnvironment bean (and so seed the holder) before this
        // bean is ever created.
        return SyntheticBeanBuildItem
                .configure(WorkerFactory.class)
                .scope(Dependent.class)
                .unremovable()
                .defaultBean()
                .addInjectionPoint(ClassType.create(TestWorkflowEnvironment.class))
                .createWith(recorder.createTestWorkerFactory())
                .setRuntimeInit()
                .done();
    }
}