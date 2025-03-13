package io.quarkiverse.temporal.graal.nettyhandling.runtime.virtual;

public interface VirtualResponseHandler {
    void handleMessage(Object msg);

    void close();
}
