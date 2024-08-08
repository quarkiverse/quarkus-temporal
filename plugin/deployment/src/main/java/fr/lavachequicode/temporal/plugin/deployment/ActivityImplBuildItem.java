package fr.lavachequicode.temporal.plugin.deployment;

import org.jboss.jandex.ClassInfo;

import io.quarkus.builder.item.MultiBuildItem;

public final class ActivityImplBuildItem extends MultiBuildItem {

    public ActivityImplBuildItem(ClassInfo classInfo) {
        this.classInfo = classInfo;
    }

    public final ClassInfo classInfo;
}
