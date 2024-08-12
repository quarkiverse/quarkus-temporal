package io.quarkiverse.temporal.deployment;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import jakarta.inject.Inject;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.deployment.discovery.NamedSimpleActivityImpl;
import io.quarkiverse.temporal.deployment.discovery.SimpleActivity;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;

public class WorkerNamedConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleActivity.class)
                    .addClass(NamedSimpleActivityImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n" +
                                    "quarkus.temporal.worker.namedWorker.max-worker-activities-per-second: 7\n" +
                                    "quarkus.temporal.worker.namedWorker.max-concurrent-activity-execution-size: 11\n" +
                                    "quarkus.temporal.worker.namedWorker.max-concurrent-workflow-task-execution-size: 13\n" +
                                    "quarkus.temporal.worker.namedWorker.max-concurrent-local-activity-execution-size: 17\n" +
                                    "quarkus.temporal.worker.namedWorker.max-task-queue-activities-per-second: 19\n" +
                                    "quarkus.temporal.worker.namedWorker.max-concurrent-workflow-task-pollers: 23\n" +
                                    "quarkus.temporal.worker.namedWorker.max-concurrent-activity-task-pollers: 29\n" +
                                    "quarkus.temporal.worker.namedWorker.local-activity-worker-only: true\n" +
                                    "quarkus.temporal.worker.namedWorker.default-deadlock-detection-timeout: 31\n" +
                                    "quarkus.temporal.worker.namedWorker.max-heartbeat-throttle-interval: 37s\n" +
                                    "quarkus.temporal.worker.namedWorker.default-heartbeat-throttle-interval: 41s\n" +
                                    "quarkus.temporal.worker.namedWorker.sticky-queue-schedule-to-start-timeout: 43s\n" +
                                    "quarkus.temporal.worker.namedWorker.disable-eager-execution: true\n" +
                                    "quarkus.temporal.worker.namedWorker.use-build-id-for-versioning: true\n" +
                                    "quarkus.temporal.worker.namedWorker.build-id: buildId\n" +
                                    "quarkus.temporal.worker.namedWorker.sticky-task-queue-drain-timeout: 47s\n"),
                            "application.properties"));

    @Inject
    WorkerFactory factory;

    @Test
    public void test() throws IllegalAccessException {
        Worker worker = factory.getWorker("namedWorker");
        Assertions.assertNotNull(worker);
        // worker config is not visible;
        WorkerOptions options = (WorkerOptions) FieldUtils.readField(worker, "options", true);
        Assertions.assertEquals(7, options.getMaxWorkerActivitiesPerSecond());
        Assertions.assertEquals(11, options.getMaxConcurrentActivityExecutionSize());
        Assertions.assertEquals(13, options.getMaxConcurrentWorkflowTaskExecutionSize());
        Assertions.assertEquals(17, options.getMaxConcurrentLocalActivityExecutionSize());
        Assertions.assertEquals(19, options.getMaxTaskQueueActivitiesPerSecond());
        Assertions.assertEquals(23, options.getMaxConcurrentWorkflowTaskPollers());
        Assertions.assertEquals(29, options.getMaxConcurrentActivityTaskPollers());
        Assertions.assertTrue(options.isLocalActivityWorkerOnly());
        Assertions.assertEquals(31, options.getDefaultDeadlockDetectionTimeout());
        Assertions.assertEquals(Duration.of(37, ChronoUnit.SECONDS), options.getMaxHeartbeatThrottleInterval());
        Assertions.assertEquals(Duration.of(41, ChronoUnit.SECONDS), options.getDefaultHeartbeatThrottleInterval());
        Assertions.assertEquals(Duration.of(43, ChronoUnit.SECONDS), options.getStickyQueueScheduleToStartTimeout());
        Assertions.assertTrue(options.isEagerExecutionDisabled());
        Assertions.assertTrue(options.isUsingBuildIdForVersioning());
        Assertions.assertEquals("buildId", options.getBuildId());
        Assertions.assertEquals(Duration.of(47, ChronoUnit.SECONDS), options.getStickyTaskQueueDrainTimeout());
    }
}
