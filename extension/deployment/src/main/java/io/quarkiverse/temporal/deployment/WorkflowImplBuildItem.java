package io.quarkiverse.temporal.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class WorkflowImplBuildItem extends MultiBuildItem {

    public WorkflowImplBuildItem(Class<?> workflow, Class<?> implementation, String[] workers) {
        this.workflow = workflow;
        this.implementation = implementation;
        this.workers = workers;
    }

    public final Class<?> workflow;
    public final Class<?> implementation;
    public final String[] workers;
}