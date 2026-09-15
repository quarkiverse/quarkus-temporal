package io.quarkiverse.temporal.deployment.stubinjection;

import io.quarkiverse.temporal.TemporalActivityStub;

public class NotAWorkflowWithStub {

    @TemporalActivityStub
    InjectedActivity activity;
}
