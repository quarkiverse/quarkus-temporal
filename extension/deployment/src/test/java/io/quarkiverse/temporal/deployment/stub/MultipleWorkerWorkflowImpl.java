package io.quarkiverse.temporal.deployment.stub;

import io.quarkiverse.temporal.TemporalWorkflow;

@TemporalWorkflow(workers = { "namedWorker", "otherWorker" })
public class MultipleWorkerWorkflowImpl implements SimpleWorkflow {

    @Override
    public void transfer() {

    }
}
