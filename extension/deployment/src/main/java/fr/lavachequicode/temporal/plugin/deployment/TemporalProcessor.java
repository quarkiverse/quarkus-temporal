package fr.lavachequicode.temporal.plugin.deployment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

import fr.lavachequicode.temporal.plugin.WorkerFactoryRecorder;
import fr.lavachequicode.temporal.plugin.WorkflowClientRecorder;
import fr.lavachequicode.temporal.plugin.WorkflowServiceStubsRecorder;
import fr.lavachequicode.temporal.plugin.config.ConnectionRuntimeConfig;
import fr.lavachequicode.temporal.plugin.config.TemporalBuildtimeConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanArchiveIndexBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.*;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.*;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.temporal.activity.ActivityInterface;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import io.temporal.workflow.WorkflowInterface;

public class TemporalProcessor {

    public static final DotName WORKFLOW_INTERFACE = DotName.createSimple(WorkflowInterface.class);

    public static final DotName ACTIVITY_INTERFACE = DotName.createSimple(ActivityInterface.class);

    private static final String FEATURE = "temporal";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void produceWorkflows(
            BeanArchiveIndexBuildItem beanArchiveBuildItem,
            BuildProducer<WorkflowImplBuildItem> producer) {
        Collection<AnnotationInstance> instances = beanArchiveBuildItem.getIndex().getAnnotations(WORKFLOW_INTERFACE);
        for (AnnotationInstance instance : instances) {
            AnnotationTarget target = instance.target();
            Collection<ClassInfo> allKnownImplementors = beanArchiveBuildItem.getIndex()
                    .getAllKnownImplementors(target.asClass().name());
            if (allKnownImplementors.size() != 1) {
                throw new IllegalStateException("Workflow " + target.asClass().name() + " must have exactly one implementor");
            }
            allKnownImplementors.forEach(implementor -> {
                producer.produce(new WorkflowImplBuildItem(implementor));
            });
        }
    }

    @BuildStep
    void produceActivities(
            CombinedIndexBuildItem beanArchiveBuildItem,
            BuildProducer<ActivityImplBuildItem> producer) {
        Collection<AnnotationInstance> instances = beanArchiveBuildItem.getIndex().getAnnotations(ACTIVITY_INTERFACE);
        for (AnnotationInstance instance : instances) {
            AnnotationTarget target = instance.target();
            Collection<ClassInfo> allKnownImplementors = beanArchiveBuildItem.getIndex()
                    .getAllKnownImplementors(target.asClass().name());
            if (allKnownImplementors.size() != 1) {
                throw new IllegalStateException("Activity " + target.asClass().name() + " must have exactly one implementor");
            }
            allKnownImplementors.forEach(implementor -> {
                producer.produce(new ActivityImplBuildItem(implementor));
            });
        }

    }

    @BuildStep
    void produceActivityBeans(
            List<ActivityImplBuildItem> activities,
            BuildProducer<AdditionalBeanBuildItem> producer) {
        activities.forEach(activity -> {
            producer.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClass(activity.classInfo.name().toString())
                    .setDefaultScope(DotName.createSimple(ApplicationScoped.class))
                    .setUnremovable()
                    .build());
        });
    }

    @BuildStep(onlyIf = EnableMock.class)
    @Produce(ConfigValidatedBuildItem.class)
    void recordWorkflowClient(Capabilities capabilities) {
        if (capabilities.isMissing("fr.lavachequicode.temporal.test")) {
            throw new ConfigurationException("Please add the quarkus-temporal-test extension to enable mocking");
        }
    }

    @BuildStep(onlyIfNot = EnableMock.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    WorkflowClientBuildItem recordWorkflowClient(
            ConnectionRuntimeConfig runtimeConfig,
            WorkflowServiceStubsRecorder recorder,
            WorkflowClientRecorder clientRecorder) {

        RuntimeValue<WorkflowServiceStubsOptions> workflowServiceStubsOptions = recorder
                .createWorkflowServiceStubsOptions(runtimeConfig);
        WorkflowServiceStubs workflowServiceStubs = recorder.createWorkflowServiceStubs(workflowServiceStubsOptions);
        return new WorkflowClientBuildItem(clientRecorder.createWorkflowClient(workflowServiceStubs));
    }

    @BuildStep
    Optional<SyntheticBeanBuildItem> produceWorkflowClientSyntheticBean(
            Optional<WorkflowClientBuildItem> workflowClientBuildItem) {

        return workflowClientBuildItem
                .map(buildItem -> SyntheticBeanBuildItem
                        .configure(WorkflowClient.class)
                        .scope(ApplicationScoped.class)
                        .unremovable()
                        .defaultBean()
                        .runtimeProxy(buildItem.workflowClient)
                        .setRuntimeInit()
                        .done());

    }

    @BuildStep(onlyIfNot = EnableMock.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    Optional<WorkerFactoryBuildItem> recordWorkflowFactory(
            Optional<WorkflowClientBuildItem> workflowClientBuildItem,
            WorkerFactoryRecorder workerFactoryRecorder) {

        return workflowClientBuildItem.map(buildItem -> {
            RuntimeValue<WorkerFactory> workerFactory = workerFactoryRecorder
                    .createWorkerFactory(buildItem.workflowClient);
            return new WorkerFactoryBuildItem(workerFactory);

        });

    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(ConfigValidatedBuildItem.class)
    Optional<InitializedWorkerFactoryBuildItem> setupWorkflowFactory(
            Optional<WorkerFactoryBuildItem> workerFactoryBuildItem,
            List<WorkflowImplBuildItem> workflowImplBuildItems,
            List<ActivityImplBuildItem> activityImplBuildItems,
            WorkerFactoryRecorder workerFactoryRecorder) {

        return workerFactoryBuildItem.map(buildItem -> {
            List<Class<?>> workflows = new ArrayList<>();

            for (var workflowBuildItem : workflowImplBuildItems) {
                workflows.add(loadClass(workflowBuildItem.classInfo));
            }

            List<Class<?>> activities = new ArrayList<>();

            for (var activityBuildItem : activityImplBuildItems) {
                activities.add(loadClass(activityBuildItem.classInfo));
            }

            workerFactoryRecorder.createWorker(buildItem.workerFactory, workflows, activities);

            return new InitializedWorkerFactoryBuildItem(buildItem.workerFactory);
        });

    }

    @BuildStep
    SyntheticBeanBuildItem produceWorkerFactorySyntheticBean(
            InitializedWorkerFactoryBuildItem workerFactoryBuildItem) {
        return SyntheticBeanBuildItem
                .configure(WorkerFactory.class)
                .scope(Singleton.class)
                .unremovable()
                .defaultBean()
                .runtimeValue(workerFactoryBuildItem.workerFactory)
                .setRuntimeInit()
                .done();
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    ServiceStartBuildItem startService(
            InitializedWorkerFactoryBuildItem workerFactoryBuildItem,
            WorkerFactoryRecorder workerFactoryRecorder,
            ShutdownContextBuildItem shutdownContextBuildItem) {
        workerFactoryRecorder.startWorkerFactory(shutdownContextBuildItem, workerFactoryBuildItem.workerFactory);
        return new ServiceStartBuildItem("TemporalWorkers");
    }

    Class<?> loadClass(ClassInfo classInfo) {
        try {
            return Thread.currentThread().getContextClassLoader()
                    .loadClass(classInfo.name().toString());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static class EnableMock implements BooleanSupplier {
        TemporalBuildtimeConfig config;

        public boolean getAsBoolean() {
            return config.enableMock();
        }
    }

}
