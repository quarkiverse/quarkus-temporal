package io.quarkiverse.temporal.deployment.stub;

import io.quarkiverse.temporal.TemporalWorkflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
@TemporalWorkflow(workers = "namedWorker")
public interface NamedSimpleWorkflow {

    @WorkflowMethod
    void transfer();
}
