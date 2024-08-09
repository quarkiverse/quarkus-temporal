package io.quarkiverse.temporal.deployment;

import org.jboss.jandex.ClassInfo;

import io.quarkus.builder.item.MultiBuildItem;

public final class WorkflowImplBuildItem extends MultiBuildItem {

    public WorkflowImplBuildItem(ClassInfo classInfo) {
        this.classInfo = classInfo;
    }

    public final ClassInfo classInfo;
}
