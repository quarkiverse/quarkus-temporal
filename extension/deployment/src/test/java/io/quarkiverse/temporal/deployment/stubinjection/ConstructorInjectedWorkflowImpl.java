package io.quarkiverse.temporal.deployment.stubinjection;

import io.quarkiverse.temporal.TemporalActivityStub;

public class ConstructorInjectedWorkflowImpl implements ConstructorInjectedWorkflow {

    private final InjectedActivity activity;

    public ConstructorInjectedWorkflowImpl(
            @TemporalActivityStub(local = true, startToCloseTimeout = "5s") InjectedActivity activity) {
        this.activity = activity;
    }

    @Override
    public void run() {
        activity.run();
    }
}
