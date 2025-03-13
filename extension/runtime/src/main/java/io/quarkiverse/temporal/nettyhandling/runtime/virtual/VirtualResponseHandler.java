package io.quarkiverse.temporal.nettyhandling.runtime.virtual;

public interface VirtualResponseHandler {
    void handleMessage(Object msg);

    void close();
}
