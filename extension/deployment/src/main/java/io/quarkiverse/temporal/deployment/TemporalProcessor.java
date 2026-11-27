package io.quarkiverse.temporal.deployment;

import static io.quarkiverse.temporal.Constants.DEFAULT_WORKER_NAME;
import static io.quarkiverse.temporal.Constants.TEMPORAL_TESTING_CAPABILITY;
import static io.quarkiverse.temporal.config.TemporalBuildtimeConfig.ChannelType.BUILT_IN;
import static io.quarkiverse.temporal.config.TemporalBuildtimeConfig.ChannelType.QUARKUS_MANAGED;
import static io.quarkus.deployment.Capability.OPENTELEMETRY_TRACER;
import static io.quarkus.runtime.metrics.MetricsFactory.MICROMETER;

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
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import io.grpc.Channel;
import io.quarkiverse.temporal.OtelHandler;
import io.quarkiverse.temporal.TemporalActivity;
import io.quarkiverse.temporal.TemporalActivityStub;
import io.quarkiverse.temporal.TemporalHealthCheck;
import io.quarkiverse.temporal.TemporalInstance;
import io.quarkiverse.temporal.TemporalWorkflow;
import io.quarkiverse.temporal.TemporalWorkflowStub;
import io.quarkiverse.temporal.WorkerFactoryHandler;
import io.quarkiverse.temporal.WorkflowClientHandler;
import io.quarkiverse.temporal.WorkflowServiceStubsDestroyer;
import io.quarkiverse.temporal.WorkflowServiceStubsHandler;
import io.quarkiverse.temporal.WorkflowStubHandler;
import io.quarkiverse.temporal.config.TemporalBuildtimeConfig;
import io.quarkiverse.temporal.config.TemporalRuntimeConfig;
import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.core.deployment.service.ServiceRegistrar;
import io.quarkus.core.deployment.service.VoidServiceBuilder0;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.grpc.deployment.GrpcClientBuildItem;
import io.quarkus.grpc.deployment.GrpcDotNames;
import io.quarkus.info.GitInfo;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.smallrye.health.deployment.spi.HealthBuildItem;
import io.temporal.activity.ActivityInterface;
import io.temporal.client.WorkflowClient;
import io.temporal.common.context.ContextPropagator;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.interceptors.WorkerInterceptor;
import io.temporal.common.interceptors.WorkflowClientInterceptor;
import io.temporal.opentracing.OpenTracingClientInterceptor;
import io.temporal.opentracing.OpenTracingWorkerInterceptor;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;
import io.temporal.workflow.WorkflowInit;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

public class TemporalProcessor {

    public static final DotName TEMPORAL_INSTANCE = DotName.createSimple(TemporalInstance.class);

    static final String WORKER_SERVICE_PREFIX = "io.quarkiverse.temporal.worker.";
    static final String OTEL_SERVICE = "io.quarkiverse.temporal.opentelemetry";
    static final String START_WORKERS_SERVICE = "io.quarkiverse.temporal.start-workers";

    public static final DotName TEMPORAL_ACTIVITY = DotName.createSimple(TemporalActivity.class);

    public static final DotName TEMPORAL_WORKFLOW = DotName.createSimple(TemporalWorkflow.class);

    public static final DotName TEMPORAL_ACTIVITY_STUB = DotName.createSimple(TemporalActivityStub.class);

    public static final DotName WORKFLOW_INTERFACE = DotName.createSimple(WorkflowInterface.class);

    public static final DotName ACTIVITY_INTERFACE = DotName.createSimple(ActivityInterface.class);

    public static final DotName WORKFLOW_INIT = DotName.createSimple(WorkflowInit.class);

    public static final DotName WORKFLOW_METHOD = DotName.createSimple(WorkflowMethod.class);

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

    @BuildStep(onlyIf = EnableTelemetry.class)
    void produceOpenTelemetryInstrumentation(
            BuildProducer<AdditionalBeanBuildItem> producer,
            Capabilities capabilities) {

        if (capabilities.isPresent(OPENTELEMETRY_TRACER)) {
            producer.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClass(OpenTracingClientInterceptor.class)
                    .setDefaultScope(DotName.createSimple(ApplicationScoped.class))
                    .setUnremovable()
                    .build());

            producer.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClass(OpenTracingWorkerInterceptor.class)
                    .setDefaultScope(DotName.createSimple(ApplicationScoped.class))
                    .setUnremovable()
                    .build());
        }
    }

    @BuildStep(onlyIf = EnableMock.class)
    @Produce(MockingValidatedBuildItem.class)
    void validateMockConfiguration(Capabilities capabilities) {
        if (capabilities.isMissing(TEMPORAL_TESTING_CAPABILITY)) {
            throw new ConfigurationException("Please add the 'quarkus-temporal-test' extension to enable mocking.");
        }
    }

    @BuildStep
    HealthBuildItem addHealthCheck(Capabilities capabilities, TemporalBuildtimeConfig buildTimeConfig) {
        if (capabilities.isPresent(Capability.SMALLRYE_HEALTH)) {
            return new HealthBuildItem(TemporalHealthCheck.class.getName(), buildTimeConfig.healthEnabled());
        } else {
            return null;
        }
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void produceWorkflows(
            TemporalBuildtimeConfig temporalBuildtimeConfig,
            CombinedIndexBuildItem beanArchiveBuildItem,
            BuildProducer<WorkflowBuildItem> workflowProducer,
            BuildProducer<WorkflowImplBuildItem> workflowImplProducer) {

        Map<DotName, Set<String>> explicitBinding = new HashMap<>();

        temporalBuildtimeConfig.worker().forEach((worker, config) -> config.workflowClasses().ifPresent(classes -> {
            for (String workflowClass : classes) {
                DotName className = DotName.createSimple(workflowClass);
                ClassInfo classInfo = beanArchiveBuildItem.getIndex().getClassByName(className);
                if (doesNotImplementAnnotatedInterface(beanArchiveBuildItem, classInfo, WORKFLOW_INTERFACE)) {
                    throw new ConfigurationException("Class " + workflowClass + " is not an workflow");
                }
                explicitBinding.computeIfAbsent(className, (k) -> new HashSet<>())
                        .add(worker);
            }
        }));

        Collection<AnnotationInstance> workflowInterfaces = beanArchiveBuildItem.getIndex().getAnnotations(WORKFLOW_INTERFACE);
        for (AnnotationInstance workflowInterface : workflowInterfaces) {
            ClassInfo workflow = workflowInterface.target().asClass();
            Collection<ClassInfo> allKnownImplementors = beanArchiveBuildItem.getIndex()
                    .getAllKnownImplementations(workflow.asClass().name());
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
                workflowImplProducer.produce(new WorkflowImplBuildItem(loadClass(workflow), loadClass(implementor), workers));
            }

            AnnotationInstance temporalWorkflow = workflow.annotation(TEMPORAL_WORKFLOW);
            workflowProducer.produce(new WorkflowBuildItem(loadClass(workflow),
                    extractWorkersFromAnnotationAndSeenWorkers(temporalWorkflow, seenWorkers)));

        }
    }

    /**
     * Validates {@link TemporalActivityStub} injection points at build time and registers the enclosing workflow
     * implementations for reflection, since their fields and constructors are accessed reflectively when the
     * workflow instance is created.
     */
    @BuildStep
    @Produce(ActivityStubInjectionValidatedBuildItem.class)
    void validateActivityStubInjectionPoints(
            CombinedIndexBuildItem beanArchiveBuildItem,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        IndexView index = beanArchiveBuildItem.getIndex();
        Set<DotName> workflowsWithInjection = new HashSet<>();
        for (AnnotationInstance instance : index.getAnnotations(TEMPORAL_ACTIVITY_STUB)) {
            AnnotationTarget target = instance.target();
            if (target.kind() != AnnotationTarget.Kind.FIELD) {
                throw new ConfigurationException("@TemporalActivityStub is only supported on fields, found on " + target);
            }
            ClassInfo declaringClass = target.asField().declaringClass();
            Type type = target.asField().type();
            String member = target.asField().name();
            if (doesNotImplementAnnotatedInterface(index, declaringClass, WORKFLOW_INTERFACE)) {
                throw new ConfigurationException("@TemporalActivityStub injection point '" + member + "' is declared in "
                        + declaringClass.name() + ", which is not a workflow implementation");
            }
            ClassInfo activity = type.kind() == Type.Kind.CLASS ? index.getClassByName(type.name()) : null;
            if (activity == null || !activity.isInterface() || !activity.hasAnnotation(ACTIVITY_INTERFACE)) {
                throw new ConfigurationException("@TemporalActivityStub injection point '" + member + "' of workflow "
                        + declaringClass.name() + " must be an @ActivityInterface, but is " + type.name());
            }
            workflowsWithInjection.add(declaringClass.name());
        }
        for (DotName workflow : workflowsWithInjection) {
            validateWorkflowInitConstructor(index, index.getClassByName(workflow));
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(workflow.toString())
                    .constructors()
                    .fields()
                    .reason("@TemporalActivityStub injection")
                    .build());
        }
    }

    /**
     * Workflows with injection points are registered through a factory, which bypasses the SDK validation of
     * {@link WorkflowInit} constructors. Apply the same rules here: the constructor must be public and take
     * exactly the parameters of the workflow method.
     */
    void validateWorkflowInitConstructor(IndexView index, ClassInfo implementation) {
        MethodInfo init = null;
        for (MethodInfo constructor : implementation.constructors()) {
            if (constructor.hasAnnotation(WORKFLOW_INIT)) {
                if (init != null) {
                    throw new ConfigurationException(
                            "Workflow " + implementation.name() + " has more than one @WorkflowInit constructor");
                }
                init = constructor;
            }
        }
        if (init == null) {
            return;
        }
        if (!java.lang.reflect.Modifier.isPublic(init.flags())) {
            throw new ConfigurationException(
                    "@WorkflowInit constructor of workflow " + implementation.name() + " must be public");
        }
        MethodInfo workflowMethod = findWorkflowMethod(index, implementation);
        if (workflowMethod == null || !workflowMethod.parameterTypes().equals(init.parameterTypes())) {
            throw new ConfigurationException("@WorkflowInit constructor of workflow " + implementation.name()
                    + " must have the same parameters as the @WorkflowMethod"
                    + (workflowMethod == null ? "" : " " + workflowMethod.name() + workflowMethod.parameterTypes()));
        }
    }

    private MethodInfo findWorkflowMethod(IndexView index, ClassInfo type) {
        if (type == null) {
            return null;
        }
        for (MethodInfo method : type.methods()) {
            if (method.hasAnnotation(WORKFLOW_METHOD)) {
                return method;
            }
        }
        for (DotName interfaceName : type.interfaceNames()) {
            MethodInfo found = findWorkflowMethod(index, index.getClassByName(interfaceName));
            if (found != null) {
                return found;
            }
        }
        return type.superName() == null ? null : findWorkflowMethod(index, index.getClassByName(type.superName()));
    }

    @BuildStep
    void produceActivities(
            TemporalBuildtimeConfig temporalBuildtimeConfig,
            CombinedIndexBuildItem beanArchiveBuildItem,
            BuildProducer<ActivityImplBuildItem> producer) {

        Map<DotName, Set<String>> explicitBinding = new HashMap<>();
        temporalBuildtimeConfig.worker().forEach((worker, config) -> config.activityClasses().ifPresent(classes -> {
            for (String activityClass : classes) {
                DotName className = DotName.createSimple(activityClass);
                ClassInfo classInfo = beanArchiveBuildItem.getIndex().getClassByName(className);
                if (doesNotImplementAnnotatedInterface(beanArchiveBuildItem, classInfo, ACTIVITY_INTERFACE)) {
                    throw new ConfigurationException("Class " + activityClass + " is not an activity");
                }
                explicitBinding.computeIfAbsent(DotName.createSimple(activityClass), (k) -> new HashSet<>())
                        .add(worker);
            }
        }));

        Collection<AnnotationInstance> instances = beanArchiveBuildItem.getIndex().getAnnotations(ACTIVITY_INTERFACE);
        for (AnnotationInstance activityInterface : instances) {
            ClassInfo activity = activityInterface.target().asClass();
            Collection<ClassInfo> allKnownImplementors = beanArchiveBuildItem.getIndex()
                    .getAllKnownImplementations(activity.name());
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
        return doesNotImplementAnnotatedInterface(beanArchiveBuildItem.getIndex(), classInfo, annotation);
    }

    boolean doesNotImplementAnnotatedInterface(IndexView index, ClassInfo classInfo, DotName annotation) {
        if (classInfo == null) {
            return true;
        }
        for (DotName interfaceName : classInfo.interfaceNames()) {
            ClassInfo interfaceInfo = index.getClassByName(interfaceName);
            if (interfaceInfo != null && interfaceInfo.hasAnnotation(annotation)) {
                return false;
            }
        }
        ClassInfo superClass = classInfo.superName() == null ? null : index.getClassByName(classInfo.superName());
        return superClass == null || doesNotImplementAnnotatedInterface(index, superClass, annotation);
    }

    String[] extractWorkersFromAnnotationAndSeenWorkers(AnnotationInstance annotation, Set<String> seenWorkers) {
        String[] workers = Stream.concat(
                Arrays.stream(extractWorkersFromAnnotation(annotation)),
                seenWorkers.stream())
                .distinct()
                .toArray(String[]::new);
        return workers.length == 0 ? new String[] { DEFAULT_WORKER_NAME } : workers;
    }

    String[] extractWorkersFromAnnotationAndExplicitBinding(ClassInfo implementor, AnnotationInstance annotation,
            Map<DotName, Set<String>> explicitBinding) {
        String[] workers = Stream.concat(
                Arrays.stream(extractWorkersFromAnnotation(annotation)),
                Stream.ofNullable(explicitBinding.get(implementor.name())).flatMap(Collection::stream))
                .distinct()
                .toArray(String[]::new);
        return workers.length == 0 ? new String[] { DEFAULT_WORKER_NAME } : workers;
    }

    /**
     * Jandex stores no value for an annotation member left at its default, so {@code workers} is absent when the
     * annotation is used without it. An absent or empty {@code workers} falls back to the default worker.
     */
    String[] extractWorkersFromAnnotation(AnnotationInstance annotation) {
        AnnotationValue workers = annotation == null ? null : annotation.value("workers");
        return workers == null ? new String[0] : workers.asStringArray();
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
        activities.forEach(activity -> producer.produce(AdditionalBeanBuildItem.builder()
                .addBeanClass(activity.clazz)
                .setDefaultScope(DotName.createSimple(ApplicationScoped.class))
                .setUnremovable()
                .build()));
    }

    @BuildStep(onlyIf = EnableQuarkusManagedChannel.class)
    GrpcClientBuildItem produceGrpcClient() {
        GrpcClientBuildItem grpcClientBuildItem = new GrpcClientBuildItem("temporal-client");
        grpcClientBuildItem.addClient(
                new GrpcClientBuildItem.ClientInfo(GrpcDotNames.CHANNEL, GrpcClientBuildItem.ClientType.CHANNEL, Set.of()));
        return grpcClientBuildItem;
    }

    @BuildStep(onlyIfNot = EnableMock.class)
    SyntheticBeanBuildItem produceWorkflowServiceStubSyntheticBean(TemporalBuildtimeConfig config,
            Optional<MetricsCapabilityBuildItem> metricsCapability) {

        boolean micrometerSupported = metricsCapability.isPresent() && metricsCapability.get().metricsSupported(MICROMETER);
        boolean quarkusManaged = !BUILT_IN.equals(config.channelType());

        SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                .configure(WorkflowServiceStubs.class)
                .scope(ApplicationScoped.class)
                .unremovable()
                .defaultBean()
                .creator(WorkflowServiceStubsHandler.class)
                .param(WorkflowServiceStubsHandler.PARAM_QUARKUS_MANAGED, quarkusManaged)
                .param(WorkflowServiceStubsHandler.PARAM_MICROMETER_SUPPORTED, micrometerSupported)
                .destroyer(WorkflowServiceStubsDestroyer.class);

        if (quarkusManaged) {
            configurator.addInjectionPoint(ClassType.create(Channel.class),
                    AnnotationInstance.builder(GrpcClient.class).value("temporal-client").build());
        }

        return configurator.done();
    }

    @BuildStep(onlyIfNot = EnableMock.class)
    SyntheticBeanBuildItem produceWorkflowClientSyntheticBean() {

        return SyntheticBeanBuildItem
                .configure(WorkflowClient.class)
                .scope(ApplicationScoped.class)
                .unremovable()
                .defaultBean()
                .addInjectionPoint(ClassType.create(WorkflowServiceStubs.class))
                .addInjectionPoint(
                        ParameterizedType.create(Instance.class, ClassType.create(WorkflowClientInterceptor.class)),
                        AnnotationInstance.builder(Any.class).build())
                .addInjectionPoint(ParameterizedType.create(Instance.class, ClassType.create(ContextPropagator.class)),
                        AnnotationInstance.builder(Any.class).build())
                .addInjectionPoint(
                        ParameterizedType.create(Instance.class, ClassType.create(DataConverter.class)),
                        AnnotationInstance.builder(Any.class).build())
                .creator(WorkflowClientHandler.class)
                .done();

    }

    @BuildStep(onlyIfNot = EnableMock.class)
    void produceWorkflowStubSyntheticBeans(
            BuildProducer<SyntheticBeanBuildItem> producer,
            List<WorkflowBuildItem> workflowImplBuildItems) {

        produceWorkflowStubSyntheticBeans(producer, workflowImplBuildItems, ClassType.create(WorkflowClient.class),
                WorkflowStubHandler.InstanceCreator.class, WorkflowStubHandler.StubCreator.class);
    }

    /**
     * Produces the {@code @TemporalWorkflowStub}-qualified {@code @Dependent} beans of every workflow: a
     * {@link io.quarkiverse.temporal.TemporalInstance} and a plain workflow stub, per worker of the workflow, plus
     * unqualified-worker variants when the workflow belongs to a single worker.
     *
     * @param clientInjectionPoint the injection point the creators use to resolve the workflow client
     * @param instanceCreator the creator of the {@code TemporalInstance} beans
     * @param stubCreator the creator of the workflow stub beans
     */
    public static void produceWorkflowStubSyntheticBeans(
            BuildProducer<SyntheticBeanBuildItem> producer,
            List<WorkflowBuildItem> workflowImplBuildItems,
            Type clientInjectionPoint,
            Class<? extends BeanCreator<Object>> instanceCreator,
            Class<? extends BeanCreator<Object>> stubCreator) {

        for (WorkflowBuildItem workflowBuildItem : workflowImplBuildItems) {
            if (workflowBuildItem.workers.length == 1) {
                produceWorkflowStubSyntheticBeans(producer, workflowBuildItem.workflow, workflowBuildItem.workers[0],
                        TemporalWorkflowStub.DEFAULT_WORKER, clientInjectionPoint, instanceCreator, stubCreator);
            }

            for (String worker : workflowBuildItem.workers) {
                produceWorkflowStubSyntheticBeans(producer, workflowBuildItem.workflow, worker, worker,
                        clientInjectionPoint, instanceCreator, stubCreator);
            }
        }
    }

    private static void produceWorkflowStubSyntheticBeans(
            BuildProducer<SyntheticBeanBuildItem> producer,
            Class<?> workflow,
            String worker,
            String qualifierWorker,
            Type clientInjectionPoint,
            Class<? extends BeanCreator<Object>> instanceCreator,
            Class<? extends BeanCreator<Object>> stubCreator) {

        producer.produce(
                SyntheticBeanBuildItem.configure(TEMPORAL_INSTANCE)
                        .types(ParameterizedType.create(TEMPORAL_INSTANCE, ClassType.create(workflow)))
                        .scope(Dependent.class)
                        .addInjectionPoint(ClassType.create(InjectionPoint.class))
                        .addInjectionPoint(clientInjectionPoint)
                        .addQualifier()
                        .annotation(TemporalWorkflowStub.class)
                        .addValue("worker", qualifierWorker)
                        .done()
                        .creator(instanceCreator)
                        .param(WorkflowStubHandler.PARAM_WORKFLOW, workflow)
                        .param(WorkflowStubHandler.PARAM_WORKER, worker)
                        .done());

        producer.produce(
                SyntheticBeanBuildItem.configure(workflow)
                        .scope(Dependent.class)
                        .addInjectionPoint(ClassType.create(InjectionPoint.class))
                        .addInjectionPoint(clientInjectionPoint)
                        .addQualifier()
                        .annotation(TemporalWorkflowStub.class)
                        .addValue("worker", qualifierWorker)
                        .done()
                        .creator(stubCreator)
                        .param(WorkflowStubHandler.PARAM_WORKFLOW, workflow)
                        .param(WorkflowStubHandler.PARAM_WORKER, worker)
                        .done());
    }

    @BuildStep(onlyIfNot = EnableMock.class)
    SyntheticBeanBuildItem produceWorkerFactorySyntheticBean() {
        return SyntheticBeanBuildItem
                .configure(WorkerFactory.class)
                .scope(Singleton.class)
                .unremovable()
                .defaultBean()
                .addInjectionPoint(ClassType.create(WorkflowClient.class))
                .addInjectionPoint(ParameterizedType.create(Instance.class, ClassType.create(WorkerInterceptor.class)),
                        AnnotationInstance.builder(Any.class).build())
                .creator(WorkerFactoryHandler.class)
                .done();
    }

    /**
     * Registers the runtime services that create the workers, bridge OpenTracing to OpenTelemetry and start the
     * workers, in that order. They are stopped in the reverse order.
     */
    @SuppressWarnings("deprecation")
    @BuildStep
    @Consume(MockingValidatedBuildItem.class)
    @Consume(ActivityStubInjectionValidatedBuildItem.class)
    void registerWorkerServices(
            ServiceRegistrar registrar,
            TemporalBuildtimeConfig config,
            Capabilities capabilities,
            List<WorkerBuildItem> workerBuildItems,
            BuildProducer<ServiceStartBuildItem> serviceStart) {

        // Build-and-run-time-fixed config cannot be required by a service: capture its values instead.
        boolean startWorkers = config.startWorkers();

        // Workers are created one after the other, in the order they were discovered.
        String previousWorkerService = null;
        for (WorkerBuildItem workerBuildItem : workerBuildItems) {
            String name = workerBuildItem.name;
            String buildId = config.worker().get(name).buildId().orElse(null);
            List<Class<?>> workflows = List.copyOf(workerBuildItem.workflows);
            List<Class<?>> activities = List.copyOf(workerBuildItem.activities);
            String workerService = WORKER_SERVICE_PREFIX + name;

            VoidServiceBuilder0 builder = registrar.forService(workerService)
                    // workers resolve the WorkerFactory and the activity beans from CDI
                    .afterBuildItem(SyntheticBeansRuntimeInitBuildItem.class);
            if (previousWorkerService != null) {
                builder = builder.after(previousWorkerService);
            }
            builder.require(TemporalRuntimeConfig.class)
                    .onStart((ctx, runtimeConfig) -> WorkerFactoryHandler.createWorker(ctx, runtimeConfig, name, buildId,
                            startWorkers, workflows, activities));
            previousWorkerService = workerService;
        }

        boolean bindOpenTracing = config.telemetryEnabled() && capabilities.isPresent(OPENTELEMETRY_TRACER);
        if (bindOpenTracing) {
            VoidServiceBuilder0 builder = registrar.forService(OTEL_SERVICE)
                    // the OpenTelemetry bean may be a runtime-init synthetic bean
                    .afterBuildItem(SyntheticBeansRuntimeInitBuildItem.class);
            if (previousWorkerService != null) {
                builder = builder.after(previousWorkerService);
            }
            builder.onStart(ctx -> OtelHandler.bindOpenTracing(ctx));
        }

        if (!startWorkers) {
            return;
        }

        VoidServiceBuilder0 builder = registrar.forService(START_WORKERS_SERVICE)
                .afterBuildItem(SyntheticBeansRuntimeInitBuildItem.class);
        if (previousWorkerService != null) {
            builder = builder.after(previousWorkerService);
        }
        if (bindOpenTracing) {
            builder = builder.after(OTEL_SERVICE);
        }
        builder.require(TemporalRuntimeConfig.class)
                .onStart((ctx, runtimeConfig) -> WorkerFactoryHandler.startWorkerFactory(ctx, runtimeConfig));
        serviceStart.produce(new ServiceStartBuildItem("TemporalWorkers"));
    }

    Class<?> loadClass(ClassInfo classInfo) {
        try {
            return Thread.currentThread().getContextClassLoader()
                    .loadClass(classInfo.name().toString());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static class EnableQuarkusManagedChannel implements BooleanSupplier {
        TemporalBuildtimeConfig config;

        public boolean getAsBoolean() {
            return !config.enableMock() && config.channelType() == QUARKUS_MANAGED;
        }
    }

    public static class EnableMock implements BooleanSupplier {
        TemporalBuildtimeConfig config;

        public boolean getAsBoolean() {
            return config.enableMock();
        }
    }

    public static class EnableTelemetry implements BooleanSupplier {
        TemporalBuildtimeConfig config;

        public boolean getAsBoolean() {
            return config.telemetryEnabled();
        }
    }

    public static class StartWorkers implements BooleanSupplier {
        TemporalBuildtimeConfig config;

        public boolean getAsBoolean() {
            return config.startWorkers();
        }
    }

}
