package io.quarkiverse.temporal;

import java.util.concurrent.TimeUnit;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;

/**
 * Exposes the gRPC channel managed by Quarkus as a {@link ManagedChannel}, which is the type the Temporal SDK
 * requires for a user provided channel.
 * <p>
 * Since Quarkus 4 the injected gRPC client channel is a plain {@link Channel} backed by the Vert.x gRPC client. Its
 * lifecycle is owned by Quarkus, so the shutdown methods here only record the request and never close the delegate.
 */
final class QuarkusManagedChannel extends ManagedChannel {

    private final Channel delegate;
    private volatile boolean shutdown;

    private QuarkusManagedChannel(Channel delegate) {
        this.delegate = delegate;
    }

    static ManagedChannel of(Channel channel) {
        if (channel instanceof ManagedChannel) {
            return (ManagedChannel) channel;
        }
        return new QuarkusManagedChannel(channel);
    }

    @Override
    public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
            MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
        return delegate.newCall(methodDescriptor, callOptions);
    }

    @Override
    public String authority() {
        return delegate.authority();
    }

    @Override
    public ManagedChannel shutdown() {
        shutdown = true;
        return this;
    }

    @Override
    public ManagedChannel shutdownNow() {
        shutdown = true;
        return this;
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return shutdown;
    }
}
