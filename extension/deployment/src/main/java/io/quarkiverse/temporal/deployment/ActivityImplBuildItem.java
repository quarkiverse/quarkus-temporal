package io.quarkiverse.temporal.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class ActivityImplBuildItem extends MultiBuildItem {

    public ActivityImplBuildItem(Class<?> clazz, String[] workers) {
        this.clazz = clazz;
        this.workers = workers;
    }

    public final Class<?> clazz;
    public final String[] workers;

}
