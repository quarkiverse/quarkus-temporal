package io.quarkiverse.temporal;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.util.TypeLiteral;

import org.jboss.logging.Logger;

import io.quarkiverse.temporal.config.TemporalBuildtimeConfig;
import io.quarkiverse.temporal.config.TemporalRuntimeConfig;
import io.quarkiverse.temporal.config.WorkerBuildtimeConfig;
import io.quarkiverse.temporal.config.WorkerRuntimeConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.info.GitInfo;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.temporal.client.WorkflowClient;
import io.temporal.common.interceptors.WorkerInterceptor;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerFactoryOptions;
import io.temporal.worker.WorkerOptions;

@Recorder
public class WorkerFactoryRecorder {

    private static final Logger log = Logger.getLogger(WorkerFactoryRecorder.class);

    public WorkerFactoryRecorder(TemporalRuntimeConfig runtimeConfig, TemporalBuildtimeConfig buildtimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.buildtimeConfig = buildtimeConfig;
    }

    final TemporalRuntimeConfig runtimeConfig;
    final TemporalBuildtimeConfig buildtimeConfig;

    WorkerFactoryOptions createWorkerFactoryOptions(
            SyntheticCreationalContext<WorkerFactory> context) {
        WorkerFactoryOptions.Builder options = WorkerFactoryOptions.newBuilder();

        var wf = runtimeConfig.workerFactory();
        options.setUsingVirtualWorkflowThreads(wf.usingVirtualWorkflowThreads());
        options.setMaxWorkflowThreadCount(wf.maxWorkflowThreadCount());
        options.setWorkflowCacheSize(wf.workflowCacheSize());

        Instance<WorkerInterceptor> interceptorInstance = context.getInjectedReference(new TypeLiteral<>() {
        }, Any.Literal.INSTANCE);

        options.setWorkerInterceptors(interceptorInstance.stream().toArray(WorkerInterceptor[]::new));

        return options.validateAndBuildWithDefaults();
    }

    public Function<SyntheticCreationalContext<WorkerFactory>, WorkerFactory> createWorkerFactory() {
        return context -> WorkerFactory.newInstance(context.getInjectedReference(WorkflowClient.class),
                createWorkerFactoryOptions(context));
    }

    WorkerOptions createWorkerOptions(WorkerRuntimeConfig workerRuntimeConfig,
                                      WorkerBuildtimeConfig workerBuildtimeConfig) {
        if (workerRuntimeConfig == null) {
            return WorkerOptions.getDefaultInstance();
        }

        // try configured id, then Git commit hash, then last resort UUID
        String buildId = workerBuildtimeConfig.buildId()
                .orElseGet(() -> {
                    Instance<GitInfo> gitInfoInstance = CDI.current().select(GitInfo.class);
                    if (!gitInfoInstance.isUnsatisfied()) {
                        final String gitCommitId = gitInfoInstance.get().latestCommitId();
                        log.infof("Worker Build Id using Git commit hash: %s", gitCommitId);
                        return gitCommitId;
                    } else {
                        final String uuid = UUID.randomUUID().toString();
                        log.warnf("Worker Build Id using UUID fallback because Git commit not found: %s", uuid);
                        // Handle the case when GitInfo bean is not available
                        return uuid; // or another fallback mechanism
                    }
                });

        WorkerOptions.Builder builder = WorkerOptions.newBuilder()
                .setMaxWorkerActivitiesPerSecond(workerRuntimeConfig.maxWorkerActivitiesPerSecond())
                .setMaxConcurrentActivityExecutionSize(workerRuntimeConfig.maxConcurrentActivityExecutionSize())
                .setMaxConcurrentWorkflowTaskExecutionSize(workerRuntimeConfig.maxConcurrentWorkflowTaskExecutionSize())
                .setMaxConcurrentLocalActivityExecutionSize(workerRuntimeConfig.maxConcurrentLocalActivityExecutionSize())
                .setMaxTaskQueueActivitiesPerSecond(workerRuntimeConfig.maxTaskQueueActivitiesPerSecond())
                .setMaxConcurrentWorkflowTaskPollers(workerRuntimeConfig.maxConcurrentWorkflowTaskPollers())
                .setMaxConcurrentActivityTaskPollers(workerRuntimeConfig.maxConcurrentActivityTaskPollers())
                .setLocalActivityWorkerOnly(workerRuntimeConfig.localActivityWorkerOnly())
                .setDefaultDeadlockDetectionTimeout(workerRuntimeConfig.defaultDeadlockDetectionTimeout())
                .setMaxHeartbeatThrottleInterval(workerRuntimeConfig.maxHeartbeatThrottleInterval())
                .setDefaultHeartbeatThrottleInterval(workerRuntimeConfig.defaultHeartbeatThrottleInterval())
                .setStickyQueueScheduleToStartTimeout(workerRuntimeConfig.stickyQueueScheduleToStartTimeout())
                .setDisableEagerExecution(workerRuntimeConfig.disableEagerExecution())
                .setUseBuildIdForVersioning(workerRuntimeConfig.useBuildIdForVersioning())
                .setStickyTaskQueueDrainTimeout(workerRuntimeConfig.stickyTaskQueueDrainTimeout())
                .setBuildId(buildId);

        workerRuntimeConfig.identity().ifPresent(builder::setIdentity);

        return builder.build();

    }

    public void createWorker(String name, List<Class<?>> workflows,
            List<Class<?>> activities) {
        WorkerFactory workerFactory = CDI.current().select(WorkerFactory.class).get();
        WorkerRuntimeConfig workerRuntimeConfig = runtimeConfig.worker().get(name);
        WorkerBuildtimeConfig workerBuildtimeConfig = buildtimeConfig.worker().get(name);

        Worker worker = workerFactory.newWorker(createQueueName(name, workerRuntimeConfig),
                createWorkerOptions(workerRuntimeConfig, workerBuildtimeConfig));
        for (var workflow : workflows) {
            worker.registerWorkflowImplementationTypes(workflow);
        }
        for (var activity : activities) {
            worker.registerActivitiesImplementations(CDI.current().select(activity).get());
        }
    }

    public void startWorkerFactory(ShutdownContext shutdownContext) {
        WorkerFactory workerFactory = CDI.current().select(WorkerFactory.class).get();
        workerFactory.start();
        shutdownContext.addShutdownTask(() -> {
            workerFactory.shutdown();
            runtimeConfig.terminationTimeout()
                    .ifPresent(timeout -> workerFactory.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS));
        });
    }

    public static String createQueueName(String name, WorkerRuntimeConfig config) {
        if (config == null) {
            return name;
        }
        return config.taskQueue().orElse(name);
    }
}