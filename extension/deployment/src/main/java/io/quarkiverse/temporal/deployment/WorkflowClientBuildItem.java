package io.quarkiverse.temporal.deployment;

import java.util.function.Function;

import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.builder.item.SimpleBuildItem;
import io.temporal.client.WorkflowClient;

public final class WorkflowClientBuildItem extends SimpleBuildItem {

    public final Function<SyntheticCreationalContext<WorkflowClient>, WorkflowClient> workflowClient;

    public WorkflowClientBuildItem(Function<SyntheticCreationalContext<WorkflowClient>, WorkflowClient> workflowClient) {
        this.workflowClient = workflowClient;
    }
}
