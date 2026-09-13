package io.quarkiverse.temporal.deployment.stubinjection;

import io.quarkiverse.temporal.TemporalActivityStub;
import io.temporal.activity.ActivityCancellationType;

public class FieldInjectedWorkflowImpl implements FieldInjectedWorkflow {

    @TemporalActivityStub(startToCloseTimeout = "10s", taskQueue = "field-tasks", cancellationType = ActivityCancellationType.WAIT_CANCELLATION_COMPLETED, retryMaximumAttempts = 3, retryDoNotRetry = {
            IllegalArgumentException.class, IllegalStateException.class })
    private InjectedActivity activity;

    @Override
    public void run() {
        activity.run();
    }
}
