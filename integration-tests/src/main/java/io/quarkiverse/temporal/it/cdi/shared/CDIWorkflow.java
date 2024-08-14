package io.quarkiverse.temporal.it.cdi.shared;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface CDIWorkflow {

    @WorkflowMethod
    void cdi();
}
