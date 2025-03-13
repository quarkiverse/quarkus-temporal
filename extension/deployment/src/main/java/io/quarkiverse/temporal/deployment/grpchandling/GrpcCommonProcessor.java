package io.quarkiverse.temporal.deployment.grpchandling;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.NativeImageConfigBuildItem;

public class GrpcCommonProcessor {

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
                .addRuntimeReinitializedClass("com.google.protobuf.UnsafeUtil");
        return builder.build();
    }

}
