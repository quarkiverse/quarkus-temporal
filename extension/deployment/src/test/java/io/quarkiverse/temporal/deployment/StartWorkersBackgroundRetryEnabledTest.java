package io.quarkiverse.temporal.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.deployment.config.DefaultSimpleActivityImpl;
import io.quarkiverse.temporal.deployment.config.DefaultSimpleWorkflowImpl;
import io.quarkiverse.temporal.deployment.config.SimpleActivity;
import io.quarkiverse.temporal.deployment.config.SimpleWorkflow;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.worker.WorkerFactory;

/**
 * Verifies non-blocking background retry mode:
 * - workers are configured to start during boot
 * - Temporal is intentionally unreachable
 * - background retry mode is enabled
 * - application startup is not blocked by worker startup failures
 */
class StartWorkersBackgroundRetryEnabledTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleWorkflow.class)
                    .addClass(DefaultSimpleWorkflowImpl.class)
                    .addClass(SimpleActivity.class)
                    .addClass(DefaultSimpleActivityImpl.class)
                    // Unreachable Temporal endpoint ensures retries happen.
                    .addAsResource(new StringAsset(
                            // Ensure worker startup is attempted as part of application startup.
                            "quarkus.temporal.start-workers: true\n" +
                            // Invalid endpoint so the background retry loop is exercised.
                                    "quarkus.temporal.connection.target: 127.0.0.1:1\n" +
                                    // Required for background mode: do not fail startup on worker start failure.
                                    "quarkus.temporal.worker-factory.fail-on-startup-error: false\n" +
                                    // Enable non-blocking worker startup retries.
                                    "quarkus.temporal.worker-factory.startup-background-retry-enabled: true\n" +
                                    // Small delay keeps test execution fast.
                                    "quarkus.temporal.worker-factory.startup-retry-delay: 10ms\n"),
                            "application.properties"));

    @Inject
    WorkerFactory factory;

    @Test
    void testStartupContinuesWithBackgroundRetries() {
        // Successful injection confirms Quarkus boot completed while retries run in background.
        Assertions.assertNotNull(factory);
    }
}
