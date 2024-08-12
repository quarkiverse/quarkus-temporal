package io.quarkiverse.temporal;

import java.util.List;

import jakarta.enterprise.inject.spi.CDI;

import io.quarkiverse.temporal.config.WorkerRuntimeConfig;
import io.quarkiverse.temporal.config.WorkersRuntimeConfig;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;

@Recorder
public class WorkerFactoryRecorder {

    public WorkerFactoryRecorder(WorkersRuntimeConfig config) {
        this.config = config;
    }

    final WorkersRuntimeConfig config;

    public RuntimeValue<WorkerFactory> createWorkerFactory(WorkflowClient workflowClient) {
        return new RuntimeValue<>(WorkerFactory.newInstance(workflowClient));
    }

    public WorkerOptions createWorkerOptions(WorkerRuntimeConfig config) {
        if (config == null) {
            return WorkerOptions.getDefaultInstance();
        }

        WorkerOptions.Builder builder = WorkerOptions.newBuilder()
                .setMaxWorkerActivitiesPerSecond(config.maxWorkerActivitiesPerSecond())
                .setMaxConcurrentActivityExecutionSize(config.maxConcurrentActivityExecutionSize())
                .setMaxConcurrentWorkflowTaskExecutionSize(config.maxConcurrentWorkflowTaskExecutionSize())
                .setMaxConcurrentLocalActivityExecutionSize(config.maxConcurrentLocalActivityExecutionSize())
                .setMaxTaskQueueActivitiesPerSecond(config.maxTaskQueueActivitiesPerSecond())
                .setMaxConcurrentWorkflowTaskPollers(config.maxConcurrentWorkflowTaskPollers())
                .setMaxConcurrentActivityTaskPollers(config.maxConcurrentActivityTaskPollers())
                .setLocalActivityWorkerOnly(config.localActivityWorkerOnly())
                .setDefaultDeadlockDetectionTimeout(config.defaultDeadlockDetectionTimeout())
                .setMaxHeartbeatThrottleInterval(config.maxHeartbeatThrottleInterval())
                .setDefaultHeartbeatThrottleInterval(config.defaultHeartbeatThrottleInterval())
                .setStickyQueueScheduleToStartTimeout(config.stickyQueueScheduleToStartTimeout())
                .setDisableEagerExecution(config.disableEagerExecution())
                .setUseBuildIdForVersioning(config.useBuildIdForVersioning())
                .setStickyTaskQueueDrainTimeout(config.stickyTaskQueueDrainTimeout());

        config.buildId().ifPresent(builder::setBuildId);
        config.identity().ifPresent(builder::setIdentity);

        return builder.build();

    }

    public void createWorker(RuntimeValue<WorkerFactory> runtimeValue, String name, List<Class<?>> workflows,
            List<Class<?>> activities) {
        WorkerFactory workerFactory = runtimeValue.getValue();
        Worker worker = workerFactory.newWorker(name, createWorkerOptions(config.workers().get(name)));
        for (var workflow : workflows) {
            worker.registerWorkflowImplementationTypes(workflow);
        }
        for (var activity : activities) {
            worker.registerActivitiesImplementations(CDI.current().select(activity).get());
        }

    }

    public void startWorkerFactory(ShutdownContext shutdownContext, RuntimeValue<WorkerFactory> runtimeValue) {
        WorkerFactory workerFactory = runtimeValue.getValue();
        workerFactory.start();
        shutdownContext.addShutdownTask(workerFactory::shutdown);
    }

}
