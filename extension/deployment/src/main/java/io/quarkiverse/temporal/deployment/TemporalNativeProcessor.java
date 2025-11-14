package io.quarkiverse.temporal.deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

import io.grpc.netty.shaded.io.grpc.netty.NettyChannelProvider;
import io.nexusrpc.handler.ServiceImpl;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageConfigBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.pkg.NativeConfig;
import io.temporal.activity.ActivityInterface;
import io.temporal.client.ActivityCompletionClient;
import io.temporal.client.WorkflowClient;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.internal.sync.AsyncInternal;
import io.temporal.internal.sync.StubMarker;
import io.temporal.serviceclient.CloudServiceStubs;
import io.temporal.serviceclient.OperatorServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.workflow.WorkflowInterface;

public class TemporalNativeProcessor {
    static final DotName DOT_NAME_WORKFLOW_INTERFACE = DotName.createSimple(WorkflowInterface.class);
    static final DotName DOT_NAME_ACTIVITY_INTERFACE = DotName.createSimple(ActivityInterface.class);
    static final DotName DOT_NAME_SERVICE_IMPL = DotName.createSimple(ServiceImpl.class);
    static final DotName DOT_NAME_STUB_MAKER = DotName.createSimple(StubMarker.class);
    static final DotName DOT_NAME_ASYNC_MAKER = DotName.createSimple(AsyncInternal.AsyncMarker.class);

    @BuildStep(onlyIf = IsNativeBuild.class)
    void indexDependencies(BuildProducer<IndexDependencyBuildItem> indexDependency) {
        indexDependency.produce(new IndexDependencyBuildItem("io.temporal", "temporal-sdk"));
        indexDependency.produce(new IndexDependencyBuildItem("io.temporal", "temporal-serviceclient"));
        indexDependency.produce(new IndexDependencyBuildItem("io.nexusrpc", "nexus-sdk"));
    }

    @BuildStep(onlyIf = IsNativeBuild.class)
    void configureNetty(BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<NativeImageConfigBuildItem> nativeImageConfig) {
        reflectiveClass.produce(ReflectiveClassBuildItem
                .builder(NettyChannelProvider.class)
                .methods()
                .reason(getClass().getName() + " built-in provider")
                .build());

        //        reflectiveClass.produce(ReflectiveClassBuildItem
        //                .builder(JacksonJsonPayloadConverter.class)
        //                .constructors()
        //                .methods()
        //                .fields()
        //                .reason("Temporal DataConverter uses JacksonJsonPayloadConverter")
        //                .build());

        nativeImageConfig.produce(NativeImageConfigBuildItem.builder()
                .addRuntimeInitializedClass("io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts")
                .build());
    }

    @BuildStep(onlyIf = IsNativeBuild.class)
    void registerProxies(CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<ReflectiveClassBuildItem> reflection,
            BuildProducer<NativeImageProxyDefinitionBuildItem> proxy) {
        registerAnnotatedClassForReflection(combinedIndexBuildItem, reflection, DOT_NAME_WORKFLOW_INTERFACE);

        registerAnnotatedClassForReflection(combinedIndexBuildItem, reflection,
                DOT_NAME_ACTIVITY_INTERFACE);

        registerAnnotatedClassForReflection(combinedIndexBuildItem, reflection, DOT_NAME_SERVICE_IMPL);

        registerAnnotationInterfaceForProxy(combinedIndexBuildItem, proxy,
                DOT_NAME_WORKFLOW_INTERFACE,
                List.of(List.of(DOT_NAME_STUB_MAKER),
                        List.of(DOT_NAME_STUB_MAKER,
                                DOT_NAME_ASYNC_MAKER)));

        registerAnnotationInterfaceForProxy(combinedIndexBuildItem, proxy, DOT_NAME_ACTIVITY_INTERFACE, List.of(List.of(
                DOT_NAME_ASYNC_MAKER)));

        registerForProxy(combinedIndexBuildItem, proxy, DotName.createSimple(WorkflowClient.class.getName()));
        registerForProxy(combinedIndexBuildItem, proxy, DotName.createSimple(ScheduleClient.class.getName()));
        registerForProxy(combinedIndexBuildItem, proxy, DotName.createSimple(CloudServiceStubs.class.getName()));
        registerForProxy(combinedIndexBuildItem, proxy, DotName.createSimple(OperatorServiceStubs.class.getName()));
        registerForProxy(combinedIndexBuildItem, proxy, DotName.createSimple(WorkflowServiceStubs.class.getName()));
        registerForProxy(combinedIndexBuildItem, proxy, DotName.createSimple(ActivityCompletionClient.class.getName()));

        registerNexusServiceForProxy(combinedIndexBuildItem, proxy,
                List.of(List.of(DOT_NAME_STUB_MAKER, DOT_NAME_ASYNC_MAKER)));
    }

    private void registerAnnotatedClassForReflection(CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<ReflectiveClassBuildItem> reflection,
            DotName annotationInterface) {
        for (var annotation : combinedIndexBuildItem.getIndex()
                .getAnnotations(annotationInterface)) {
            for (var implementor : combinedIndexBuildItem.getIndex()
                    .getAllKnownImplementors(annotation.target().asClass().toString())) {
                reflection.produce(ReflectiveClassBuildItem
                        .builder(implementor.asClass().toString())
                        .constructors()
                        .methods()
                        .reason(annotationInterface.toString() + " implementation")
                        .build());
            }
        }
    }

    private void registerAnnotationInterfaceForProxy(CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<NativeImageProxyDefinitionBuildItem> proxy,
            DotName annotationInterface,
            List<List<DotName>> additionalClasses) {
        for (var annotation : combinedIndexBuildItem.getIndex()
                .getAnnotations(annotationInterface)) {
            registerForProxy(proxy, additionalClasses, annotation.target().asClass().toString());
        }
    }

    private void registerNexusServiceForProxy(CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<NativeImageProxyDefinitionBuildItem> proxy,
            List<List<DotName>> additionalClasses) {
        for (var annotation : combinedIndexBuildItem.getIndex()
                .getAnnotations(DotName.createSimple(ServiceImpl.class.getName()))) {
            registerForProxy(proxy, additionalClasses, annotation.target().asClass().toString());
            var serviceValue = annotation.value("service");
            registerForProxy(proxy, additionalClasses, serviceValue.asClass().name().toString());
        }
    }

    private void registerForProxy(BuildProducer<NativeImageProxyDefinitionBuildItem> proxy,
            List<List<DotName>> additionalClasses,
            String className) {
        var types = new ArrayList<String>();
        types.add(className);
        proxy.produce(new NativeImageProxyDefinitionBuildItem(types));

        if (!additionalClasses.isEmpty()) {
            for (var aClasses : additionalClasses) {
                var aTypes = new ArrayList<String>();
                aTypes.add(className);
                aClasses.forEach(dotName -> aTypes.add(dotName.toString()));
                proxy.produce(new NativeImageProxyDefinitionBuildItem(aTypes));
            }
        }
    }

    private void registerForProxy(CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<NativeImageProxyDefinitionBuildItem> proxy,
            DotName classDotName) {
        ClassInfo classInfo = combinedIndexBuildItem.getIndex().getClassByName(classDotName);
        proxy.produce(new NativeImageProxyDefinitionBuildItem(classDotName.toString()));
        List<DotName> iNames = classInfo.asClass().interfaceNames();
        if (!iNames.isEmpty()) {
            var types = new ArrayList<String>();
            types.add(classDotName.toString());
            iNames.forEach(dotName -> types.add(dotName.toString()));
            proxy.produce(new NativeImageProxyDefinitionBuildItem(types));
        }
    }

    static class IsNativeBuild implements BooleanSupplier {
        private final NativeConfig nativeConfig;

        public IsNativeBuild(final NativeConfig nativeConfig) {
            this.nativeConfig = nativeConfig;
        }

        @Override
        public boolean getAsBoolean() {
            return nativeConfig.enabled() && !nativeConfig.sourcesOnly();
        }
    }
}
