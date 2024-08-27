package io.quarkiverse.temporal.deployment;

import static io.quarkiverse.temporal.Constants.DEFAULT_WORKER_NAME;
import static io.quarkus.deployment.Capability.OPENTELEMETRY_TRACER;

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
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;

import io.quarkiverse.temporal.TemporalActivity;
import io.quarkiverse.temporal.TemporalWorkflow;
import io.quarkiverse.temporal.TemporalWorkflowStub;
import io.quarkiverse.temporal.WorkerFactoryRecorder;
import io.quarkiverse.temporal.WorkflowClientRecorder;
import io.quarkiverse.temporal.WorkflowServiceStubsRecorder;
import io.quarkiverse.temporal.WorkflowStubRecorder;
import io.quarkiverse.temporal.config.TemporalBuildtimeConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
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
import io.quarkus.info.GitInfo;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.temporal.activity.ActivityInterface;
import io.temporal.client.WorkflowClient;
import io.temporal.common.context.ContextPropagator;
import io.temporal.common.interceptors.WorkerInterceptor;
import io.temporal.common.interceptors.WorkflowClientInterceptor;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;
import io.temporal.workflow.WorkflowInterface;

public class TemporalProcessor {

    public static final DotName TEMPORAL_ACTIVITY = DotName.createSimple(TemporalActivity.class);

    public static final DotName TEMPORAL_WORKFLOW = DotName.createSimple(TemporalWorkflow.class);

    public static final DotName WORKFLOW_INTERFACE = DotName.createSimple(WorkflowInterface.class);

    public static final DotName ACTIVITY_INTERFACE = DotName.createSimple(ActivityInterface.class);

    private static final String FEATURE = "temporal";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void additionalBeans(BuildProducer<AdditionalBeanBuildItem> producer) {
        producer.produce(new AdditionalBeanBuildItem(TemporalWorkflowStub.class));
    }

    @BuildStep
    void unremovableBean(BuildProducer<UnremovableBeanBuildItem> producer) {
        producer.produce(new UnremovableBeanBuildItem(
                new UnremovableBeanBuildItem.BeanTypeExclusion(DotName.createSimple(GitInfo.class))));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    OpenTelemetryValidatedBuildItem validateOpenTelemetryInstrumentation(WorkflowClientRecorder clientRecorder,
            WorkerFactoryRecorder factoryRecorder,
            Capabilities capabilities) {
        return new OpenTelemetryValidatedBuildItem(capabilities.isPresent(OPENTELEMETRY_TRACER));
    }

    @BuildStep(onlyIf = EnableMock.class)
    @Produce(MockingValidatedBuildItem.class)
    void validateMockConfiguration(Capabilities capabilities) {
        if (capabilities.isMissing("io.quarkiverse.temporal.test")) {
            throw new ConfigurationException("Please add the 'quarkus-temporal-test' extension to enable mocking.");
        }
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void produceWorkflows(
            TemporalBuildtimeConfig temporalBuildtimeConfig,
            CombinedIndexBuildItem beanArchiveBuildItem,
            BuildProducer<WorkflowBuildItem> workflowProducer,
            BuildProducer<WorkflowImplBuildItem> producer) {

        Map<DotName, Set<String>> explicitBinding = new HashMap<>();

        temporalBuildtimeConfig.worker().forEach((worker, config) -> {
            config.workflowClasses().ifPresent(classes -> {
                for (String workflowClass : classes) {
                    DotName className = DotName.createSimple(workflowClass);
                    ClassInfo classInfo = beanArchiveBuildItem.getIndex().getClassByName(className);
                    if (doesNotImplementAnnotatedInterface(beanArchiveBuildItem, classInfo, WORKFLOW_INTERFACE)) {
                        throw new ConfigurationException("Class " + workflowClass + " is not an workflow");
                    }
                    explicitBinding.computeIfAbsent(className, (k) -> new HashSet<>())
                            .add(worker);
                }
            });
        });

        Collection<AnnotationInstance> workflowInterfaces = beanArchiveBuildItem.getIndex().getAnnotations(WORKFLOW_INTERFACE);
        for (AnnotationInstance workflowInterface : workflowInterfaces) {
            ClassInfo workflow = workflowInterface.target().asClass();
            Collection<ClassInfo> allKnownImplementors = beanArchiveBuildItem.getIndex()
                    .getAllKnownImplementors(workflow.asClass().name());
            Set<String> seenWorkers = new HashSet<>();
            for (ClassInfo implementor : allKnownImplementors) {
                AnnotationInstance temporalWorkflow = implementor.annotation(TEMPORAL_WORKFLOW);
                String[] workers = extractWorkersFromAnnotationAndExplicitBinding(implementor, temporalWorkflow,
                        explicitBinding);
                if (!Collections.disjoint(seenWorkers, Arrays.asList(workers))) {
                    throw new IllegalStateException(
                            "Workflow " + workflow.name() + " has more than one implementor on worker");
                }
                Collections.addAll(seenWorkers, workers);
                producer.produce(new WorkflowImplBuildItem(loadClass(workflow), loadClass(implementor), workers));
            }
            workflowProducer.produce(new WorkflowBuildItem(loadClass(workflow), seenWorkers.toArray(new String[0])));
        }
    }

    @BuildStep
    void produceActivities(
            TemporalBuildtimeConfig temporalBuildtimeConfig,
            CombinedIndexBuildItem beanArchiveBuildItem,
            BuildProducer<ActivityImplBuildItem> producer) {

        Map<DotName, Set<String>> explicitBinding = new HashMap<>();
        temporalBuildtimeConfig.worker().forEach((worker, config) -> {
            config.activityClasses().ifPresent(classes -> {
                for (String activityClass : classes) {
                    DotName className = DotName.createSimple(activityClass);
                    ClassInfo classInfo = beanArchiveBuildItem.getIndex().getClassByName(className);
                    if (doesNotImplementAnnotatedInterface(beanArchiveBuildItem, classInfo, ACTIVITY_INTERFACE)) {
                        throw new ConfigurationException("Class " + activityClass + " is not an activity");
                    }
                    explicitBinding.computeIfAbsent(DotName.createSimple(activityClass), (k) -> new HashSet<>())
                            .add(worker);
                }
            });
        });

        Collection<AnnotationInstance> instances = beanArchiveBuildItem.getIndex().getAnnotations(ACTIVITY_INTERFACE);
        for (AnnotationInstance activityInterface : instances) {
            ClassInfo activity = activityInterface.target().asClass();
            Collection<ClassInfo> allKnownImplementors = beanArchiveBuildItem.getIndex()
                    .getAllKnownImplementors(activity.name());
            Set<String> seenWorkers = new HashSet<>();
            for (ClassInfo implementor : allKnownImplementors) {
                AnnotationInstance temporalActivity = implementor.annotation(TEMPORAL_ACTIVITY);
                String[] workers = extractWorkersFromAnnotationAndExplicitBinding(implementor, temporalActivity,
                        explicitBinding);
                if (!Collections.disjoint(seenWorkers, Arrays.asList(workers))) {
                    throw new IllegalStateException(
                            "Activity " + activity.name() + " has more than one implementor on worker");
                }
                Collections.addAll(seenWorkers, workers);
                producer.produce(new ActivityImplBuildItem(loadClass(implementor), workers));
            }
        }
    }

    boolean doesNotImplementAnnotatedInterface(CombinedIndexBuildItem beanArchiveBuildItem, ClassInfo classInfo,
            DotName annotation) {
        return classInfo == null
                || classInfo.interfaceNames().stream()
                        .noneMatch(interfaceName -> {
                            ClassInfo interfaceInfo = beanArchiveBuildItem.getIndex().getClassByName(interfaceName);
                            return interfaceInfo.hasAnnotation(annotation);
                        });
    }

    String[] extractWorkersFromAnnotationAndExplicitBinding(ClassInfo implementor, AnnotationInstance annotation,
            Map<DotName, Set<String>> explicitBinding) {
        return (annotation == null && !explicitBinding.containsKey(implementor.name()))
                ? new String[] { DEFAULT_WORKER_NAME }
                : Stream.concat(
                        Stream.ofNullable(annotation).flatMap(x -> Arrays.stream(x.value("workers").asStringArray())),
                        Stream.ofNullable(explicitBinding.get(implementor.name())).flatMap(Collection::stream))
                        .distinct()
                        .toArray(String[]::new);
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
                        .add(workflowImplBuildItem.implementation);
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

    @BuildStep(onlyIfNot = EnableMock.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    WorkflowClientBuildItem recordWorkflowClient(
            WorkflowServiceStubsRecorder recorder,
            WorkflowClientRecorder clientRecorder,
            OpenTelemetryValidatedBuildItem openTelemetryValidatedBuildItem) {

        WorkflowServiceStubs workflowServiceStubs = recorder.createWorkflowServiceStubs();
        boolean isOpenTelemetryEnabled = openTelemetryValidatedBuildItem.isEnabled();
        return new WorkflowClientBuildItem(
                clientRecorder.createWorkflowClient(workflowServiceStubs, isOpenTelemetryEnabled));
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
                        .addInjectionPoint(
                                ParameterizedType.create(Instance.class, ClassType.create(WorkflowClientInterceptor.class)),
                                AnnotationInstance.builder(Any.class).build())
                        .addInjectionPoint(ParameterizedType.create(Instance.class, ClassType.create(ContextPropagator.class)),
                                AnnotationInstance.builder(Any.class).build())
                        .createWith(buildItem.workflowClient)
                        .setRuntimeInit()
                        .done());

    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void produceWorkflowStubSyntheticBeans(
            BuildProducer<SyntheticBeanBuildItem> producer,
            List<WorkflowBuildItem> workflowImplBuildItems,
            WorkflowStubRecorder recorder) {

        for (WorkflowBuildItem workflowBuildItem : workflowImplBuildItems) {
            if (workflowBuildItem.workers.length == 1) {
                producer.produce(
                        SyntheticBeanBuildItem.configure(workflowBuildItem.workflow)
                                .scope(Dependent.class)
                                .addInjectionPoint(ClassType.create(InjectionPoint.class))
                                .addInjectionPoint(ClassType.create(WorkflowClient.class))
                                .addQualifier()
                                .annotation(TemporalWorkflowStub.class)
                                .addValue("worker", TemporalWorkflowStub.DEFAULT_WORKER)
                                .done()
                                .createWith(
                                        recorder.createWorkflowStub(workflowBuildItem.workflow, workflowBuildItem.workers[0]))
                                .setRuntimeInit()
                                .done());
            }

            for (String worker : workflowBuildItem.workers) {
                producer.produce(
                        SyntheticBeanBuildItem.configure(workflowBuildItem.workflow)
                                .scope(Dependent.class)
                                .addInjectionPoint(ClassType.create(InjectionPoint.class))
                                .addInjectionPoint(ClassType.create(WorkflowClient.class))
                                .addQualifier()
                                .annotation(TemporalWorkflowStub.class)
                                .addValue("worker", worker)
                                .done()
                                .createWith(recorder.createWorkflowStub(workflowBuildItem.workflow, worker))
                                .setRuntimeInit()
                                .done());
            }
        }

    }

    @BuildStep(onlyIfNot = EnableMock.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    SyntheticBeanBuildItem produceWorkerFactorySyntheticBean(
            WorkerFactoryRecorder workerFactoryRecorder,
            OpenTelemetryValidatedBuildItem openTelemetryValidatedBuildItem) {
        return SyntheticBeanBuildItem
                .configure(WorkerFactory.class)
                .scope(Singleton.class)
                .unremovable()
                .defaultBean()
                .addInjectionPoint(ClassType.create(WorkflowClient.class))
                .addInjectionPoint(ParameterizedType.create(Instance.class, ClassType.create(WorkerInterceptor.class)),
                        AnnotationInstance.builder(Any.class).build())
                .createWith(workerFactoryRecorder.createWorkerFactory(openTelemetryValidatedBuildItem.isEnabled()))
                .setRuntimeInit()
                .done();
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(MockingValidatedBuildItem.class)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    @Produce(WorkerFactoryInitializedBuildItem.class)
    void setupWorkerFactory(
            List<WorkerBuildItem> workerBuildItems,
            WorkerFactoryRecorder workerFactoryRecorder) {

        for (WorkerBuildItem workerBuildItem : workerBuildItems) {
            workerFactoryRecorder.createWorker(workerBuildItem.name,
                    workerBuildItem.workflows, workerBuildItem.activities);
        }
    }

    @BuildStep(onlyIf = StartWorkers.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(WorkerFactoryInitializedBuildItem.class)
    ServiceStartBuildItem startWorkers(
            WorkerFactoryRecorder workerFactoryRecorder,
            ShutdownContextBuildItem shutdownContextBuildItem,
            OpenTelemetryValidatedBuildItem openTelemetryValidatedBuildItem) {
        workerFactoryRecorder.startWorkerFactory(shutdownContextBuildItem, openTelemetryValidatedBuildItem.isEnabled());
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
