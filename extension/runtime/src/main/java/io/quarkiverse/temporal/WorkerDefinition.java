package io.quarkiverse.temporal;

import java.util.List;

public final class WorkerDefinition {

    public WorkerDefinition(String name, List<Class<?>> workflows, List<Class<?>> activities) {
        this.name = name;
        this.workflows = workflows;
        this.activities = activities;
    }

    final String name;
    final List<Class<?>> workflows;
    final List<Class<?>> activities;
}
