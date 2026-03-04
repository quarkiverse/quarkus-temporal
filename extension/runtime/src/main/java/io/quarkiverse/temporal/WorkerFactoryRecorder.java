package io.quarkiverse.temporal;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.temporal.client.WorkflowClient;
import io.temporal.common.interceptors.WorkerInterceptor;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerFactoryOptions;
import io.temporal.worker.WorkerOptions;

/**
 * Quarkus recorder responsible for creating and starting Temporal {@link WorkerFactory} instances.
 *
 * In Quarkus extensions, recorders are invoked from deployment build steps at runtime init to
 * perform runtime wiring that cannot be done at augmentation time. This recorder handles:
 *
 * <ul>
 * <li>Building worker factory/worker options from extension config</li>
 * <li>Creating workers and registering discovered workflow/activity implementations</li>
 * <li>Starting workers with configurable startup resilience (blocking retries or background retries)</li>
 * <li>Registering graceful shutdown behavior with {@link ShutdownContext}</li>
 * </ul>
 */
@Recorder
public class WorkerFactoryRecorder {

    private static final Logger log = Logger.getLogger(WorkerFactoryRecorder.class);

    /**
     * The runtime configuration for Temporal.
     */
    final RuntimeValue<TemporalRuntimeConfig> runtimeConfig;

    /**
     * The build-time configuration for Temporal.
     */
    final TemporalBuildtimeConfig buildtimeConfig;

    public WorkerFactoryRecorder(RuntimeValue<TemporalRuntimeConfig> runtimeConfig, TemporalBuildtimeConfig buildtimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.buildtimeConfig = buildtimeConfig;
    }

    /**
     * Builds {@link WorkerFactoryOptions} from runtime config and CDI-discovered worker interceptors.
     *
     * This method is invoked by the synthetic bean creator during runtime init.
     */
    WorkerFactoryOptions createWorkerFactoryOptions(
            SyntheticCreationalContext<WorkerFactory> context) {
        WorkerFactoryOptions.Builder options = WorkerFactoryOptions.newBuilder();

        var wf = runtimeConfig.getValue().workerFactory();
        options.setUsingVirtualWorkflowThreads(wf.usingVirtualWorkflowThreads());
        options.setMaxWorkflowThreadCount(wf.maxWorkflowThreadCount());
        options.setWorkflowCacheSize(wf.workflowCacheSize());

        Instance<WorkerInterceptor> interceptorInstance = context.getInjectedReference(new TypeLiteral<>() {
        }, Any.Literal.INSTANCE);

        options.setWorkerInterceptors(interceptorInstance.stream().toArray(WorkerInterceptor[]::new));

        return options.validateAndBuildWithDefaults();
    }

    public Function<SyntheticCreationalContext<WorkerFactory>, WorkerFactory> createWorkerFactory() {
        // Synthetic bean creator for WorkerFactory. Actual start happens in a separate runtime build step.
        return context -> WorkerFactory.newInstance(context.getInjectedReference(WorkflowClient.class),
                createWorkerFactoryOptions(context));
    }

    /**
     * Builds {@link WorkerOptions} for a specific worker name from extension config.
     */
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
        // Workers are created during runtime init and registered with workflow/activity implementations.
        // Starting polling threads is intentionally deferred to startWorkerFactory(...).
        WorkerFactory workerFactory = CDI.current().select(WorkerFactory.class).get();
        WorkerRuntimeConfig workerRuntimeConfig = runtimeConfig.getValue().worker().get(name);
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
        // This method is invoked from TemporalProcessor.startWorkers(...) during runtime init.
        // Any exception thrown here can fail Quarkus startup.
        WorkerFactory workerFactory = CDI.current().select(WorkerFactory.class).get();
        var workerFactoryConfig = runtimeConfig.getValue().workerFactory();
        boolean failOnStartupError = workerFactoryConfig.failOnStartupError();
        int maxAttempts = Math.max(workerFactoryConfig.startupMaxAttempts(), 1);
        long retryDelayMillis = Math.max(workerFactoryConfig.startupRetryDelay().toMillis(), 0);
        boolean startupBackgroundRetryEnabled = workerFactoryConfig.startupBackgroundRetryEnabled();

        // Background retry mode intentionally decouples app startup from Temporal availability.
        if (startupBackgroundRetryEnabled) {
            if (!failOnStartupError) {
                // In this mode Quarkus startup proceeds immediately while worker startup is retried asynchronously.
                startWorkerFactoryInBackground(shutdownContext, workerFactory, retryDelayMillis);
                return;
            }
            log.warn(
                    "quarkus.temporal.worker-factory.startup-background-retry-enabled=true requires quarkus.temporal.worker-factory.fail-on-startup-error=false. Falling back to blocking startup behavior.");
        }

        // Blocking startup mode: retry a bounded number of times, then either fail or continue based on config.
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // Temporal SDK contacts server capabilities as part of start(); this can fail if server is unavailable.
                workerFactory.start();
                shutdownContext.addShutdownTask(() -> {
                    workerFactory.shutdown();
                    runtimeConfig.getValue().terminationTimeout()
                            .ifPresent(timeout -> workerFactory.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS));
                });
                return;
            } catch (RuntimeException e) {
                lastError = e;
                if (attempt < maxAttempts) {
                    log.warnf(e,
                            "Temporal worker startup failed (attempt %d/%d). Retrying in %d ms",
                            attempt, maxAttempts, retryDelayMillis);
                    if (retryDelayMillis > 0) {
                        try {
                            Thread.sleep(retryDelayMillis);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            if (failOnStartupError) {
                                throw new IllegalStateException(
                                        "Interrupted while retrying Temporal worker startup",
                                        ie);
                            }
                            log.error(
                                    "Interrupted while retrying Temporal worker startup. Continuing without started workers because quarkus.temporal.worker-factory.fail-on-startup-error=false",
                                    ie);
                            return;
                        }
                    }
                    continue;
                }

                if (failOnStartupError) {
                    throw e;
                }
                log.error(
                        "Temporal worker startup failed. Continuing because quarkus.temporal.worker-factory.fail-on-startup-error=false",
                        e);
                return;
            }
        }

        if (failOnStartupError && lastError != null) {
            throw lastError;
        }
    }

    /**
     * Starts a daemon retry loop for worker startup.
     *
     * Used when startup should not block waiting for Temporal availability.
     */
    void startWorkerFactoryInBackground(ShutdownContext shutdownContext, WorkerFactory workerFactory, long retryDelayMillis) {
        // Keep retrying on a daemon thread until startup succeeds or Quarkus shuts down.
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicBoolean started = new AtomicBoolean(false);
        ExecutorService startupExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "temporal-worker-startup");
            thread.setDaemon(true);
            return thread;
        });

        startupExecutor.submit(() -> {
            int attempt = 0;
            while (running.get() && !started.get()) {
                attempt++;
                try {
                    workerFactory.start();
                    started.set(true);
                    log.infof("Temporal workers started in background (attempt %d)", attempt);
                    return;
                } catch (RuntimeException e) {
                    log.warnf(e,
                            "Temporal worker startup failed in background (attempt %d). Retrying in %d ms",
                            attempt, retryDelayMillis);
                    if (retryDelayMillis > 0) {
                        try {
                            Thread.sleep(retryDelayMillis);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.debug("Temporal worker startup background retry interrupted", ie);
                            return;
                        }
                    }
                }
            }
        });

        shutdownContext.addShutdownTask(() -> {
            // Stop background retries first so no new start attempts race with shutdown.
            running.set(false);
            startupExecutor.shutdownNow();
            // Avoid calling shutdown on a factory that never managed to start.
            if (started.get()) {
                workerFactory.shutdown();
                runtimeConfig.getValue().terminationTimeout()
                        .ifPresent(timeout -> workerFactory.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS));
            }
        });
    }

    public static String createQueueName(String name, WorkerRuntimeConfig config) {
        if (config == null) {
            return name;
        }
        return config.taskQueue().orElse(name);
    }
}
