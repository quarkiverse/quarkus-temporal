package io.quarkiverse.temporal.graal.netty.runtime.virtual;

public interface VirtualResponseHandler {
    void handleMessage(Object msg);

    void close();
}
