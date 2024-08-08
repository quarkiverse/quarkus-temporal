package fr.lavachequicode.temporal.plugin.spi;

import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.runtime.RuntimeValue;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.WorkerFactory;

public final class WorkflowClientBuildItem extends SimpleBuildItem {

    public final WorkflowClient workflowClient;

    public WorkflowClientBuildItem(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }
}
