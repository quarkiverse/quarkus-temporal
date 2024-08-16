package io.quarkiverse.temporal.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class WorkflowBuildItem extends MultiBuildItem {

    public WorkflowBuildItem(Class<?> workflow, String[] workers) {
        this.workflow = workflow;
        this.workers = workers;
    }

    public final Class<?> workflow;

    public final String[] workers;
}
