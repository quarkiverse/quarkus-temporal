package io.quarkiverse.temporal.it.cdi.defaultWorker;

import io.quarkiverse.temporal.TemporalActivityStub;
import io.quarkiverse.temporal.it.cdi.shared.CDIActivity;
import io.quarkiverse.temporal.it.cdi.shared.CDIWorkflow;

public class CDIWorkflowImpl implements CDIWorkflow {

    // The activity stub is created by the extension when the workflow instance is created,
    // this is equivalent to calling Workflow.newActivityStub(CDIActivity.class, options) in a field initializer
    @TemporalActivityStub(startToCloseTimeout = "2s", scheduleToCloseTimeout = "5000s", retryInitialInterval = "1s", retryMaximumInterval = "20s", retryBackoffCoefficient = 2, retryMaximumAttempts = 5000)
    CDIActivity cdiActivity;

    @Override
    public void cdi() {
        cdiActivity.cdi();
    }
}
