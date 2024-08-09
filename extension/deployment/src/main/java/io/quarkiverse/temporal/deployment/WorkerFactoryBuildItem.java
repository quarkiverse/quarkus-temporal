package io.quarkiverse.temporal.deployment;

import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.runtime.RuntimeValue;
import io.temporal.worker.WorkerFactory;

public final class WorkerFactoryBuildItem extends SimpleBuildItem {

    public final RuntimeValue<WorkerFactory> workerFactory;

    public WorkerFactoryBuildItem(RuntimeValue<WorkerFactory> workerFactory) {
        this.workerFactory = workerFactory;
    }
}
