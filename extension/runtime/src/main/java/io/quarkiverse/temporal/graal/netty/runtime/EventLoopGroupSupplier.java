package io.quarkiverse.temporal.graal.netty.runtime;

import java.util.function.Supplier;

import io.grpc.netty.shaded.io.netty.channel.EventLoopGroup;

/**
 * A non-generic {@link Supplier} of {@link EventLoopGroup}, usable as a {@code ServiceRegistrar} service type.
 */
public interface EventLoopGroupSupplier extends Supplier<EventLoopGroup> {
}
