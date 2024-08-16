package io.quarkiverse.temporal.deployment.stub;

import io.quarkiverse.temporal.TemporalWorkflow;

@TemporalWorkflow(workers = "namedWorker")
public class NamedSimpleWorkflowImpl implements SimpleWorkflow {

    @Override
    public void transfer() {

    }
}
