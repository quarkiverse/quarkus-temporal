package io.quarkiverse.temporal.it.freshness.defaultWorker;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface FreshnessWorkflow {

    @WorkflowMethod
    String ping(String input);
}
