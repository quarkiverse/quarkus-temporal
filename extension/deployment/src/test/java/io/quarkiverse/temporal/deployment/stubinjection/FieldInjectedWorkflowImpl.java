package io.quarkiverse.temporal.deployment.stubinjection;

import io.quarkiverse.temporal.TemporalActivityStub;

public class FieldInjectedWorkflowImpl implements FieldInjectedWorkflow {

    @TemporalActivityStub(startToCloseTimeout = "10s")
    private InjectedActivity activity;

    @Override
    public void run() {
        activity.run();
    }
}
