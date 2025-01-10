package io.quarkiverse.temporal.deployment.graalvm;

import java.util.Collection;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

import io.grpc.NameResolverProvider;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.internal.ReadableBuffers;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelProvider;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedPackageBuildItem;

public class NettyShadedProcessor {

    static final DotName NAME_RESOLVER_PROVIDER = DotName.createSimple(NameResolverProvider.class.getName());

    @BuildStep
    void registerReflecttionsNettyShaded(BuildProducer<ReflectiveClassBuildItem> reflections,
            CombinedIndexBuildItem combinedIndex) {
        ReflectiveClassBuildItem buildItem = ReflectiveClassBuildItem.builder(
                "io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel",
                "io.grpc.netty.shaded.io.netty.util.internal.NativeLibraryUtil",
                "io.grpc.netty.shaded.io.netty.util.ReferenceCountUtil",
                "io.grpc.netty.shaded.io.netty.buffer.AbstractByteBufAllocator",
                "io.grpc.netty.shaded.io.netty.channel.epoll.Epoll",
                "io.grpc.netty.shaded.io.netty.channel.epoll.EpollChannelOption",
                "io.grpc.netty.shaded.io.netty.channel.epoll.EpollEventLoopGroup",
                "io.grpc.netty.shaded.io.netty.channel.epoll.EpollServerSocketChannel",
                "io.grpc.netty.shaded.io.netty.channel.epoll.EpollSocketChannel",
                "io.grpc.internal.PickFirstLoadBalancerProvider",
                "io.grpc.protobuf.services.internal.HealthCheckingRoundRobinLoadBalancerProvider")
                .constructors(true)
                .methods(true)
                .fields(true)
                .build();

        reflections.produce(buildItem);

        String prefixPackageDir = "io.grpc.netty.shaded.io.netty.util.internal.shaded.org.jctools.queues";
        reflections.produce(ReflectiveClassBuildItem.builder(
                prefixPackageDir + ".MpscArrayQueueProducerIndexField",
                prefixPackageDir + ".MpscArrayQueueProducerLimitField",
                prefixPackageDir + ".MpscArrayQueueConsumerIndexField",
                prefixPackageDir + ".BaseMpscLinkedArrayQueueProducerFields",
                prefixPackageDir + ".BaseMpscLinkedArrayQueueColdProducerFields",
                prefixPackageDir + ".BaseMpscLinkedArrayQueueConsumerFields",
                "io.grpc.internal.DnsNameResolverProvider")
                .constructors()
                .methods()
                .fields()
                .build());

        Collection<ClassInfo> nrs = combinedIndex.getIndex().getAllKnownSubclasses(NAME_RESOLVER_PROVIDER);
        for (ClassInfo nr : nrs) {
            reflections.produce(ReflectiveClassBuildItem.builder(nr.name().toString())
                    .constructors()
                    .methods()
                    .build());
        }

        reflections.produce(ReflectiveClassBuildItem.builder(DnsNameResolverProvider.class)
                .constructors()
                .methods()
                .build());
        reflections.produce(ReflectiveClassBuildItem.builder("io.grpc.util.SecretRoundRobinLoadBalancerProvider$Provider")
                .constructors()
                .methods()
                .build());
        reflections.produce(ReflectiveClassBuildItem.builder(NettyChannelProvider.class)
                .constructors()
                .methods()
                .build());
        reflections.produce(ReflectiveClassBuildItem.builder(ReadableBuffers.class)
                .constructors()
                .methods()
                .fields()
                .build());

    }

    /**
     * The next step fails to package the project
     * /* [ERROR] [error]: Build step io.quarkus.deployment.steps.NativeImageResourcesStep#registerPackageResources threw an
     * exception: java.lang.StringIndexOutOfBoundsException: begin 0, end -1, length 65
     */
    //    @BuildStep
    //    void addNativeResourceForNettyShaded(BuildProducer<NativeImageResourceDirectoryBuildItem> resourceBuildItem) {
    //        resourceBuildItem.produce(new NativeImageResourceDirectoryBuildItem("META-INF"));
    //    }
    @BuildStep
    void runTimeInitializationForNettyShaded(
            BuildProducer<RuntimeInitializedPackageBuildItem> runtimePackages) {
        runtimePackages.produce(new RuntimeInitializedPackageBuildItem("io.grpc.netty.shaded.io.netty.channel.epoll"));
        runtimePackages.produce(new RuntimeInitializedPackageBuildItem("io.grpc.netty.shaded.io.netty.channel.unix"));
        runtimePackages.produce(new RuntimeInitializedPackageBuildItem("io.grpc.netty.shaded.io.netty.handler.ssl"));
        runtimePackages.produce(new RuntimeInitializedPackageBuildItem("io.grpc.netty.shaded.io.netty.internal.tcnative"));
    }
}
