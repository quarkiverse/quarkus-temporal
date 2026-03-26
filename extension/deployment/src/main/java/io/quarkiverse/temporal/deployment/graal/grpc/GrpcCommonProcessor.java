package io.quarkiverse.temporal.deployment.graal.grpc;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageConfigBuildItem;

public class GrpcCommonProcessor {

    @BuildStep
    public IndexDependencyBuildItem indexProtobuf() {
        // needed with reflection lookup
        return new IndexDependencyBuildItem("com.google.protobuf", "protobuf-java");
    }

    @BuildStep
    NativeImageConfigBuildItem nativeImageConfiguration() {
        NativeImageConfigBuildItem.Builder builder = NativeImageConfigBuildItem.builder()
                .addRuntimeInitializedClass("io.grpc.netty.shaded.io.grpc.netty.Utils$ByteBufAllocatorPreferDirectHolder")
                .addRuntimeInitializedClass("io.grpc.netty.shaded.io.grpc.netty.Utils$ByteBufAllocatorPreferHeapHolder")
                // substitutions are runtime-only, Utils have to be substituted until we cannot use EPoll
                // in native. NettyServerBuilder and NettyChannelBuilder would "bring in" Utils in build time
                // if they were not marked as runtime initialized:
                .addRuntimeInitializedClass("io.grpc.netty.shaded.io.grpc.netty.Utils")
                .addRuntimeInitializedClass("io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder")
                .addRuntimeInitializedClass("io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder")
                .addRuntimeInitializedClass("io.grpc.internal.RetriableStream")
                .addRuntimeInitializedClass("com.google.protobuf.JavaFeaturesProto")
                .addRuntimeInitializedClass("com.google.protobuf.UnsafeUtil");
        return builder.build();
    }

}
