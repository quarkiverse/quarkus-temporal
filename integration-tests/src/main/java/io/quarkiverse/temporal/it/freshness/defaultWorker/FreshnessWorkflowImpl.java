package io.quarkiverse.temporal.it.freshness.defaultWorker;

public class FreshnessWorkflowImpl implements FreshnessWorkflow {

    @Override
    public String ping(String input) {
        return "pong:" + input;
    }
}
