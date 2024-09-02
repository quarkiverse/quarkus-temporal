package io.quarkiverse.temporal;

public interface TemporalInstance<T> {

    T workflowId(String workflowId);
}
