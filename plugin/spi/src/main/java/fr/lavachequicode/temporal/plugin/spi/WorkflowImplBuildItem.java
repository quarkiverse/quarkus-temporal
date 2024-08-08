package fr.lavachequicode.temporal.plugin.spi;

import io.quarkus.builder.item.MultiBuildItem;
import org.jboss.jandex.ClassInfo;

public final class WorkflowImplBuildItem extends MultiBuildItem {

    public WorkflowImplBuildItem(ClassInfo classInfo) {
        this.classInfo = classInfo;
    }

    public final ClassInfo classInfo;
}
