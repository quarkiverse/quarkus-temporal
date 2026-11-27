package io.quarkiverse.temporal.test.deployment;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.ParameterizedType;

import io.quarkiverse.temporal.deployment.TemporalProcessor;
import io.quarkiverse.temporal.deployment.WorkflowBuildItem;
import io.quarkiverse.temporal.test.TestWorkflowHandler;
import io.quarkiverse.temporal.test.TestWorkflowStubHandler;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
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

    @BuildStep(onlyIf = TemporalProcessor.EnableMock.class)
    SyntheticBeanBuildItem produceTestEnvironmentSyntheticBean() {
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
                .creator(TestWorkflowHandler.EnvironmentCreator.class)
                .done();
    }

    @BuildStep(onlyIf = TemporalProcessor.EnableMock.class)
    SyntheticBeanBuildItem produceWorkflowClientSyntheticBean() {

        return SyntheticBeanBuildItem
                .configure(WorkflowClient.class)
                .scope(ApplicationScoped.class)
                .unremovable()
                .defaultBean()
                .addInjectionPoint(ClassType.create(TestWorkflowEnvironment.class))
                .creator(TestWorkflowHandler.WorkflowClientCreator.class)
                .done();
    }

    @BuildStep(onlyIf = TemporalProcessor.EnableMock.class)
    SyntheticBeanBuildItem produceWorkerFactorySyntheticBean() {
        // @Dependent (not a normal scope): io.temporal.worker.WorkerFactory is a final class
        // with only a private constructor and cannot be CDI-proxied. A fresh instance is
        // instead resolved from MockTestEnvironmentHolder at every injection point - see
        // MockTestEnvironmentHolder / MockTestWorkflowResetCallback for how freshness is
        // still achieved per test.
        // The TestWorkflowEnvironment injection point below is not read directly by
        // WorkerFactoryCreator any more, but it must stay: declaring it is what forces
        // ArC to create the TestWorkflowEnvironment bean (and so seed the holder) before this
        // bean is ever created.
        return SyntheticBeanBuildItem
                .configure(WorkerFactory.class)
                .scope(Dependent.class)
                .unremovable()
                .defaultBean()
                .addInjectionPoint(ClassType.create(TestWorkflowEnvironment.class))
                .creator(TestWorkflowHandler.WorkerFactoryCreator.class)
                .done();
    }

    @BuildStep(onlyIf = TemporalProcessor.EnableMock.class)
    void produceWorkflowStubSyntheticBeans(
            BuildProducer<SyntheticBeanBuildItem> producer,
            List<WorkflowBuildItem> workflowImplBuildItems) {
        // Mock-mode counterpart of TemporalProcessor#produceWorkflowStubSyntheticBeans: same set
        // of @TemporalWorkflowStub-qualified @Dependent beans, but resolving the WorkflowClient
        // from MockTestEnvironmentHolder (prepared one test ahead) instead of injecting the CDI
        // WorkflowClient bean directly - see TestWorkflowStubHandler for why.
        TemporalProcessor.produceWorkflowStubSyntheticBeans(producer, workflowImplBuildItems,
                ClassType.create(TestWorkflowEnvironment.class),
                TestWorkflowStubHandler.InstanceCreator.class, TestWorkflowStubHandler.StubCreator.class);
    }
}
