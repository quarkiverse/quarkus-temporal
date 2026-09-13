package io.quarkiverse.temporal.it.cdi.namedWorker;

import io.quarkiverse.temporal.TemporalActivityStub;
import io.quarkiverse.temporal.TemporalWorkflow;
import io.quarkiverse.temporal.it.cdi.shared.CDIActivity;
import io.quarkiverse.temporal.it.cdi.shared.CDIWorkflow;

@TemporalWorkflow(workers = "namedWorker")
public class CDIWorkflowImpl implements CDIWorkflow {

    @TemporalActivityStub(startToCloseTimeout = "2s")
    CDIActivity cdiActivity;

    @Override
    public void cdi() {
        cdiActivity.cdi();
    }
}
