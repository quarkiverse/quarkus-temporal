package io.quarkiverse.temporal;

import java.util.List;
import java.util.function.Function;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.inject.spi.InjectionPoint;

import io.quarkiverse.temporal.config.TemporalRuntimeConfig;
import io.quarkiverse.temporal.config.WorkerRuntimeConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;

@Recorder
public class WorkerFactoryRecorder {

    public WorkerFactoryRecorder(TemporalRuntimeConfig config) {
        this.config = config;
    }

    final TemporalRuntimeConfig config;

    public Function<SyntheticCreationalContext<WorkerFactory>, WorkerFactory> createWorkerFactory() {
        return context -> WorkerFactory.newInstance(context.getInjectedReference(WorkflowClient.class));
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

    public String createQueueName(String name, WorkerRuntimeConfig config) {
        if (config == null) {
            return name;
        }
        return config.taskQueue().orElse(name);
    }

    public void createWorker(String name, List<Class<?>> workflows,
            List<Class<?>> activities) {
        WorkerFactory workerFactory = CDI.current().select(WorkerFactory.class).get();
        WorkerRuntimeConfig workerRuntimeConfig = config.worker().get(name);
        Worker worker = workerFactory.newWorker(createQueueName(name, workerRuntimeConfig),
                createWorkerOptions(workerRuntimeConfig));
        for (var workflow : workflows) {
            worker.registerWorkflowImplementationTypes(workflow);
        }
        for (var activity : activities) {
            worker.registerActivitiesImplementations(CDI.current().select(activity).get());
        }

    }

    public <T> Function<SyntheticCreationalContext<T>, T> createWorkflowStub(Class<T> workflow, String name) {
        return context -> {
            InjectionPoint injectionPoint = context.getInjectedReference(InjectionPoint.class);
            TemporalWorkflowStub annotation = (TemporalWorkflowStub) injectionPoint.getQualifiers().stream()
                    .filter(x -> x instanceof TemporalWorkflowStub).findFirst().orElse(null);
            WorkerRuntimeConfig workerRuntimeConfig = config.worker().get(name);
            WorkflowOptions.Builder options = WorkflowOptions.newBuilder()
                    .setTaskQueue(createQueueName(name, workerRuntimeConfig));
            if (annotation != null && !TemporalWorkflowStub.DEFAULT_WORKFLOW_ID.equals(annotation.workflowId())) {
                options.setWorkflowId(annotation.workflowId());
            }
            return context.getInjectedReference(WorkflowClient.class).newWorkflowStub(workflow, options.build());
        };
    }

    public void startWorkerFactory(ShutdownContext shutdownContext) {
        WorkerFactory workerFactory = CDI.current().select(WorkerFactory.class).get();
        workerFactory.start();
        shutdownContext.addShutdownTask(workerFactory::shutdown);
    }
}
