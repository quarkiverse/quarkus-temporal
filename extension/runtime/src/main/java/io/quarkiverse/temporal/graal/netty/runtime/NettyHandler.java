package io.quarkiverse.temporal.graal.netty.runtime;

import java.util.concurrent.TimeUnit;

import io.grpc.netty.shaded.io.netty.channel.EventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.quarkus.core.StartContext;

/**
 * Creates the shaded Netty event loop groups. Invoked from {@code ServiceRegistrar} services registered by the
 * deployment processor.
 */
public final class NettyHandler {

    private NettyHandler() {
    }

    /**
     * Returns a supplier that lazily creates the event loop group on first use. The group, if created, is shut down
     * when the service stops.
     */
    public static EventLoopGroupSupplier createEventLoop(StartContext ctx, int nThreads) {
        LazyEventLoopGroupSupplier supplier = new LazyEventLoopGroupSupplier(nThreads);
        ctx.onStop(supplier::shutdown);
        return supplier;
    }

    static final class LazyEventLoopGroupSupplier implements EventLoopGroupSupplier {

        private final int nThreads;
        private volatile EventLoopGroup val;

        LazyEventLoopGroupSupplier(int nThreads) {
            this.nThreads = nThreads;
        }

        @Override
        public EventLoopGroup get() {
            if (val == null) {
                synchronized (this) {
                    if (val == null) {
                        val = new NioEventLoopGroup(nThreads);
                    }
                }
            }
            return val;
        }

        void shutdown() {
            EventLoopGroup group;
            synchronized (this) {
                group = val;
                val = null;
            }
            if (group != null) {
                group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
            }
        }
    }
}
