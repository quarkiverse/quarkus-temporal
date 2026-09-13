package io.quarkiverse.temporal.deployment.stubinjection;

import io.quarkiverse.temporal.TemporalActivityStub;
import io.temporal.workflow.WorkflowInit;

public class InvalidInitWorkflowImpl implements InitInjectedWorkflow {

    @TemporalActivityStub(startToCloseTimeout = "5s")
    InjectedActivity activity;

    // does not match the parameters of InitInjectedWorkflow#run(String)
    @WorkflowInit
    public InvalidInitWorkflowImpl(int unexpected) {
    }

    @Override
    public String run(String name) {
        return name;
    }
}
