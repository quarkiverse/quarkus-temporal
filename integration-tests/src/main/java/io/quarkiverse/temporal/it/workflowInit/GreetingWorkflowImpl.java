package io.quarkiverse.temporal.it.workflowInit;

import io.quarkiverse.temporal.TemporalActivityStub;
import io.quarkiverse.temporal.it.cdi.shared.CDIActivity;
import io.temporal.workflow.WorkflowInit;

/**
 * Combines a {@link WorkflowInit} constructor, which receives the workflow input, with an injected activity stub.
 */
public class GreetingWorkflowImpl implements GreetingWorkflow {

    private final String greeting;

    @TemporalActivityStub(startToCloseTimeout = "2s")
    CDIActivity cdiActivity;

    @WorkflowInit
    public GreetingWorkflowImpl(String name) {
        this.greeting = "Hello " + name;
    }

    @Override
    public String greet(String name) {
        cdiActivity.cdi();
        return greeting;
    }
}
