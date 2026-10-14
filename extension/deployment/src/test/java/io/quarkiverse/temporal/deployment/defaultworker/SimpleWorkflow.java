package io.quarkiverse.temporal.deployment.defaultworker;

import io.quarkiverse.temporal.TemporalWorkflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@TemporalWorkflow
@WorkflowInterface
public interface SimpleWorkflow {

    @WorkflowMethod
    void transfer();
}
