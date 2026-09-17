package io.quarkiverse.temporal.deployment.stubinjection;

import io.quarkiverse.temporal.TemporalActivityStub;
import io.temporal.workflow.WorkflowInit;

public class InitInjectedWorkflowImpl implements InitInjectedWorkflow {

    private final String name;

    @TemporalActivityStub(local = true, startToCloseTimeout = "5s")
    InjectedActivity activity;

    @WorkflowInit
    public InitInjectedWorkflowImpl(String name) {
        this.name = name;
    }

    @Override
    public String run(String name) {
        activity.run();
        return this.name;
    }
}
