package io.quarkiverse.temporal.deployment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

import io.quarkiverse.temporal.ActivityImpl;
import io.quarkiverse.temporal.WorkerFactoryRecorder;
import io.quarkiverse.temporal.WorkflowClientRecorder;
import io.quarkiverse.temporal.WorkflowImpl;
import io.quarkiverse.temporal.WorkflowServiceStubsRecorder;
import io.quarkiverse.temporal.config.TemporalBuildtimeConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanArchiveIndexBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.temporal.activity.ActivityInterface;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;
import io.temporal.workflow.WorkflowInterface;

public class TemporalProcessor {

    public static final DotName ACTIVITY_IMPL = DotName.createSimple(ActivityImpl.class);

    public static final DotName WORKFLOW_IMPL = DotName.createSimple(WorkflowImpl.class);

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
            Set<String> seenWorkers = new HashSet<>();
            allKnownImplementors.forEach(implementor -> {
                AnnotationInstance annotation = implementor.annotation(WORKFLOW_IMPL);
                String[] workers = annotation == null ? new String[] { "<default>" }
                        : annotation.value("workers").asStringArray();

                if (!Collections.disjoint(seenWorkers, Arrays.asList(workers))) {
                    throw new IllegalStateException(
                            "Workflow " + target.asClass().name() + " has more than one implementor on worker");
                }
                Collections.addAll(seenWorkers, workers);
                producer.produce(new WorkflowImplBuildItem(loadClass(implementor), workers));
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
            Set<String> seenWorkers = new HashSet<>();
            allKnownImplementors.forEach(implementor -> {
                AnnotationInstance annotation = implementor.annotation(ACTIVITY_IMPL);
                String[] workers = annotation == null ? new String[] { "<default>" }
                        : annotation.value("workers").asStringArray();
                if (!Collections.disjoint(seenWorkers, Arrays.asList(workers))) {
                    throw new IllegalStateException(
                            "Activity " + target.asClass().name() + " has more than one implementor on worker");
                }
                Collections.addAll(seenWorkers, workers);
                producer.produce(new ActivityImplBuildItem(loadClass(implementor), workers));
            });
        }
    }

    @BuildStep
    void produceWorkers(
            List<WorkflowImplBuildItem> workflowImplBuildItems,
            List<ActivityImplBuildItem> activityImplBuildItems,
            BuildProducer<WorkerBuildItem> producer) {

        Set<String> workers = new HashSet<>();

        Map<String, List<Class<?>>> workflowsByWorker = new HashMap<>();

        for (WorkflowImplBuildItem workflowImplBuildItem : workflowImplBuildItems) {
            for (String worker : workflowImplBuildItem.workers) {
                workers.add(worker);
                workflowsByWorker.computeIfAbsent(worker, (w) -> new ArrayList<>())
                        .add(workflowImplBuildItem.clazz);
            }
        }

        Map<String, List<Class<?>>> activitiesByWorker = new HashMap<>();

        for (ActivityImplBuildItem activityImplBuildItem : activityImplBuildItems) {
            for (String worker : activityImplBuildItem.workers) {
                workers.add(worker);
                activitiesByWorker.computeIfAbsent(worker, (w) -> new ArrayList<>())
                        .add(activityImplBuildItem.clazz);
            }
        }

        for (String worker : workers) {
            producer.produce(new WorkerBuildItem(worker, workflowsByWorker.getOrDefault(worker, List.of()),
                    activitiesByWorker.getOrDefault(worker, List.of())));
        }

    }

    @BuildStep
    void produceActivityBeans(
            List<ActivityImplBuildItem> activities,
            BuildProducer<AdditionalBeanBuildItem> producer) {
        activities.forEach(activity -> {
            producer.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClass(activity.clazz)
                    .setDefaultScope(DotName.createSimple(ApplicationScoped.class))
                    .setUnremovable()
                    .build());
        });
    }

    @BuildStep(onlyIf = EnableMock.class)
    @Produce(ConfigValidatedBuildItem.class)
    void recordWorkflowClient(Capabilities capabilities) {
        if (capabilities.isMissing("io.quarkiverse.temporal.test")) {
            throw new ConfigurationException("Please add the quarkus-temporal-test extension to enable mocking");
        }
    }

    @BuildStep(onlyIfNot = EnableMock.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    WorkflowClientBuildItem recordWorkflowClient(
            WorkflowServiceStubsRecorder recorder,
            WorkflowClientRecorder clientRecorder) {

        WorkflowServiceStubs workflowServiceStubs = recorder.createWorkflowServiceStubs();
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
            List<WorkerBuildItem> workerBuildItems,
            WorkerFactoryRecorder workerFactoryRecorder) {

        return workerFactoryBuildItem.map(buildItem -> {
            for (WorkerBuildItem workerBuildItem : workerBuildItems) {
                workerFactoryRecorder.createWorker(buildItem.workerFactory, workerBuildItem.name,
                        workerBuildItem.workflows, workerBuildItem.activities);
            }
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

    @BuildStep(onlyIf = StartWorkers.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    ServiceStartBuildItem startWorkers(
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

    public static class StartWorkers implements BooleanSupplier {
        TemporalBuildtimeConfig config;

        public boolean getAsBoolean() {
            return config.startWorkers();
        }
    }

}
