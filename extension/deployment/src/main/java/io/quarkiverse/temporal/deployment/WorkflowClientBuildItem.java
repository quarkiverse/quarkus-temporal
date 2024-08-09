package io.quarkiverse.temporal.deployment;

import io.quarkus.builder.item.SimpleBuildItem;
import io.temporal.client.WorkflowClient;

public final class WorkflowClientBuildItem extends SimpleBuildItem {

    public final WorkflowClient workflowClient;

    public WorkflowClientBuildItem(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }
}
