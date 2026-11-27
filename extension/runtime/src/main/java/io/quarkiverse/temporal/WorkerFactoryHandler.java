package io.quarkiverse.temporal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.util.TypeLiteral;

import org.jboss.logging.Logger;

import io.quarkiverse.temporal.config.*;
import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.core.StartContext;
import io.quarkus.info.GitInfo;
import io.temporal.client.WorkflowClient;
import io.temporal.common.VersioningBehavior;
import io.temporal.common.WorkerDeploymentVersion;
import io.temporal.common.interceptors.WorkerInterceptor;
import io.temporal.worker.*;

/**
 * Handler responsible for creating and starting Temporal {@link WorkerFactory} instances.
 *
 * The {@link WorkerFactory} synthetic bean is created by this class acting as a {@link BeanCreator}. The remaining
 * runtime wiring is performed by {@code ServiceRegistrar} services registered by the deployment processor, which call
 * the static methods of this class:
 *
 * <ul>
 * <li>Building worker factory/worker options from extension config</li>
 * <li>Creating workers and registering discovered workflow/activity implementations</li>
 * <li>Starting workers with configurable startup resilience (blocking retries or background retries)</li>
 * <li>Registering graceful shutdown behavior with the service {@link StartContext}</li>
 * </ul>
 */
public class WorkerFactoryHandler implements BeanCreator<WorkerFactory> {

    private static final Logger log = Logger.getLogger(WorkerFactoryHandler.class);

    @Override
    public WorkerFactory create(SyntheticCreationalContext<WorkerFactory> context) {
        // Actual start happens in a separate runtime service.
        return WorkerFactory.newInstance(context.getInjectedReference(WorkflowClient.class),
                createWorkerFactoryOptions(context, TemporalConfigs.runtime()));
    }

    /**
     * Builds {@link WorkerFactoryOptions} from runtime config and CDI-discovered worker interceptors.
     *
     * This method is invoked by the synthetic bean creator.
     */
    static WorkerFactoryOptions createWorkerFactoryOptions(
            SyntheticCreationalContext<WorkerFactory> context, TemporalRuntimeConfig runtimeConfig) {
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

    /**
     * Builds {@link WorkerOptions} for a specific worker name from extension config.
     */
    static WorkerOptions createWorkerOptions(WorkerRuntimeConfig workerRuntimeConfig,
            Optional<String> configuredBuildId) {
        if (workerRuntimeConfig == null) {
            return WorkerOptions.getDefaultInstance();
        }

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
                .setStickyTaskQueueDrainTimeout(workerRuntimeConfig.stickyTaskQueueDrainTimeout());

        WorkerDeploymentOptions workerDeploymentOptions = createWorkerDeploymentOptions(workerRuntimeConfig);

        if (workerDeploymentOptions != null) {
            builder.setDeploymentOptions(workerDeploymentOptions);

            boolean legacyWorkerVersioningConfigured = configuredBuildId.isPresent() ||
                    workerRuntimeConfig.useBuildIdForVersioning();

            if (legacyWorkerVersioningConfigured) {
                log.warn(
                        "Both 'quarkus.temporal.worker.<build-id/use-build-id-for-versioning>' and 'quarkus.temporal.worker.deployment-options' are configured. "
                                + "The legacy 'build-id/use-build-id-for-versioning' property is deprecated and will be ignored in favor of "
                                + "'deployment-options', which uses the new Worker Deployment Version API.");
            }
        } else {
            builder.setUseBuildIdForVersioning(workerRuntimeConfig.useBuildIdForVersioning());
            // try configured id, then Git commit hash, then last resort UUID
            String buildId = configuredBuildId
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
            builder.setBuildId(buildId);
        }

        workerRuntimeConfig.identity().ifPresent(builder::setIdentity);

        return builder.build();

    }

    private static WorkerDeploymentOptions createWorkerDeploymentOptions(WorkerRuntimeConfig workerRuntimeConfig) {
        Optional<WorkerDeploymentOptionsRuntimeConfig> workerDeploymentOptionsRuntimeConfig = workerRuntimeConfig
                .deploymentOptions();

        if (workerDeploymentOptionsRuntimeConfig.isEmpty()) {
            return null;
        }

        WorkerDeploymentOptions.Builder workerDeploymentOptionsBuilder = WorkerDeploymentOptions.newBuilder();
        workerDeploymentOptionsBuilder
                .setUseVersioning(workerDeploymentOptionsRuntimeConfig.get().useVersioning().orElse(false));
        workerDeploymentOptionsBuilder
                .setDefaultVersioningBehavior(workerDeploymentOptionsRuntimeConfig.get().defaultVersioningBehavior()
                        .orElse(VersioningBehavior.UNSPECIFIED));

        Optional<String> deploymentName = workerDeploymentOptionsRuntimeConfig.get().name();
        Optional<String> buildId = workerDeploymentOptionsRuntimeConfig.get().buildId();
        Optional<String> deploymentVersion = workerDeploymentOptionsRuntimeConfig.get().version();

        boolean hasNameOrBuildId = deploymentName.isPresent() || buildId.isPresent();

        if (hasNameOrBuildId) {
            if (deploymentName.isEmpty() || buildId.isEmpty()) {
                throw new IllegalArgumentException(
                        "name and buildId must both be set when either is specified");
            }
            if (deploymentVersion.isPresent()) {
                throw new IllegalArgumentException(
                        "version is exclusive with deploymentName and buildId");
            }
            workerDeploymentOptionsBuilder.setVersion(new WorkerDeploymentVersion(deploymentName.get(), buildId.get()));
        } else {
            deploymentVersion.ifPresent(
                    version -> workerDeploymentOptionsBuilder.setVersion(WorkerDeploymentVersion.fromCanonicalString(version)));
        }

        return workerDeploymentOptionsBuilder.build();
    }

    /**
     * Creates a worker and registers its workflow/activity implementations.
     *
     * @param ctx the start context of the service creating the worker
     * @param runtimeConfig the Temporal runtime configuration
     * @param name the worker name
     * @param buildId the build-time configured build id of the worker, or {@code null} if none is configured
     * @param startWorkers whether workers are started, in which case the activity implementations are registered
     * @param workflows the workflow implementations
     * @param activities the activity implementations
     */
    public static void createWorker(StartContext ctx, TemporalRuntimeConfig runtimeConfig, String name,
            String buildId, boolean startWorkers, List<Class<?>> workflows, List<Class<?>> activities) {
        Optional<String> configuredBuildId = Optional.ofNullable(buildId);
        Runnable registration = () -> doCreateWorker(runtimeConfig, name, configuredBuildId, startWorkers, workflows,
                activities);
        WorkerRegistrationRegistry.record(registration);
        // Remove only this application's entry: the registry is static and may outlive this application.
        ctx.onStop(() -> WorkerRegistrationRegistry.remove(registration));
        doCreateWorker(runtimeConfig, name, configuredBuildId, startWorkers, workflows, activities);
    }

    private static void doCreateWorker(TemporalRuntimeConfig runtimeConfig, String name, Optional<String> buildId,
            boolean startWorkers, List<Class<?>> workflows, List<Class<?>> activities) {
        // Workers are created during runtime init (or replayed per test in mock mode) and
        // registered with workflow/activity implementations.
        // Starting polling threads is intentionally deferred to startWorkerFactory(...).
        WorkerFactory workerFactory = CDI.current().select(WorkerFactory.class).get();
        WorkerRuntimeConfig workerRuntimeConfig = runtimeConfig.worker().get(name);

        Worker worker = workerFactory.newWorker(createQueueName(name, workerRuntimeConfig),
                createWorkerOptions(workerRuntimeConfig, buildId));
        for (var workflow : workflows) {
            registerWorkflow(worker, workflow);
        }
        if (startWorkers) {
            for (var activity : activities) {
                worker.registerActivitiesImplementations(CDI.current().select(activity).get());
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void registerWorkflow(Worker worker, Class<?> workflow) {
        if (!ActivityStubInjector.hasInjectionPoints(workflow)) {
            worker.registerWorkflowImplementationTypes(workflow);
            return;
        }
        // Workflows with @TemporalActivityStub injection points are created by a factory so that the
        // stubs can be created inside the workflow context before the workflow method runs. The factory
        // receives the encoded workflow input so that @WorkflowInit constructors keep working.
        ActivityStubInjector injector = ActivityStubInjector.forClass(workflow);
        worker.registerWorkflowImplementationFactory((Class) injector.workflowInterface(), injector::newInstance,
                WorkflowImplementationOptions.getDefaultInstance());
    }

    public static void startWorkerFactory(StartContext ctx, TemporalRuntimeConfig runtimeConfig) {
        // This method is invoked from the service registered by TemporalProcessor.registerWorkerServices(...).
        // Any exception thrown here can fail Quarkus startup.
        WorkerFactory workerFactory = CDI.current().select(WorkerFactory.class).get();
        var workerFactoryConfig = runtimeConfig.workerFactory();
        boolean failOnStartupError = workerFactoryConfig.failOnStartupError();
        int maxAttempts = Math.max(workerFactoryConfig.startupMaxAttempts(), 1);
        long retryDelayMillis = Math.max(workerFactoryConfig.startupRetryDelay().toMillis(), 0);
        boolean startupBackgroundRetryEnabled = workerFactoryConfig.startupBackgroundRetryEnabled();

        // Background retry mode intentionally decouples app startup from Temporal availability.
        if (startupBackgroundRetryEnabled) {
            if (!failOnStartupError) {
                // In this mode Quarkus startup proceeds immediately while worker startup is retried asynchronously.
                startWorkerFactoryInBackground(ctx, runtimeConfig, workerFactory, retryDelayMillis);
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
                ctx.onStop(() -> {
                    workerFactory.shutdown();
                    runtimeConfig.terminationTimeout()
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
    static void startWorkerFactoryInBackground(StartContext ctx, TemporalRuntimeConfig runtimeConfig,
            WorkerFactory workerFactory, long retryDelayMillis) {
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

        ctx.onStop(() -> {
            // Stop background retries first so no new start attempts race with shutdown.
            running.set(false);
            startupExecutor.shutdownNow();
            // Avoid calling shutdown on a factory that never managed to start.
            if (started.get()) {
                workerFactory.shutdown();
                runtimeConfig.terminationTimeout()
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
