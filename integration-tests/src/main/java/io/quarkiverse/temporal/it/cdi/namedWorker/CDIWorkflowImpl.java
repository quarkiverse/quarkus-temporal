package io.quarkiverse.temporal.it.cdi.namedWorker;

import io.quarkiverse.temporal.TemporalActivityStub;
import io.quarkiverse.temporal.TemporalWorkflow;
import io.quarkiverse.temporal.it.cdi.shared.CDIActivity;
import io.quarkiverse.temporal.it.cdi.shared.CDIWorkflow;

@TemporalWorkflow(workers = "namedWorker")
public class CDIWorkflowImpl implements CDIWorkflow {

    private final CDIActivity cdiActivity;

    // Activity stubs can also be injected through the constructor, which allows the field to be final
    public CDIWorkflowImpl(@TemporalActivityStub(startToCloseTimeout = "2s") CDIActivity cdiActivity) {
        this.cdiActivity = cdiActivity;
    }

    @Override
    public void cdi() {
        cdiActivity.cdi();
    }
}
