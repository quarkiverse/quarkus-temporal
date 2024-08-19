package io.quarkiverse.temporal.deployment;

import java.util.List;

import io.quarkus.builder.item.MultiBuildItem;

public final class WorkerBuildItem extends MultiBuildItem {

    WorkerBuildItem(String name, List<Class<?>> workflows, List<Class<?>> activities) {
        this.name = name;
        this.workflows = workflows;
        this.activities = activities;
    }

    final String name;
    final List<Class<?>> workflows;
    final List<Class<?>> activities;

}
