package io.quarkiverse.temporal.deployment.stubinjection;

import io.quarkiverse.temporal.TemporalActivityStub;

public class InvalidTypeWorkflowImpl implements InvalidTypeWorkflow {

    @TemporalActivityStub
    String notAnActivity;

    @Override
    public void run() {
    }
}
