package io.quarkiverse.temporal.deployment.stubinjection;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ConstructorInjectedWorkflow {

    @WorkflowMethod
    void run();
}
