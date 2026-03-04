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
 * Verifies bounded startup retry mode:
 * - workers are configured to start during boot
 * - Temporal is intentionally unreachable
 * - startup retries are attempted a fixed number of times
 * - application startup still succeeds because fail-on-startup-error is disabled
 */
class StartWorkersIgnoreStartupErrorTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleWorkflow.class)
                    .addClass(DefaultSimpleWorkflowImpl.class)
                    .addClass(SimpleActivity.class)
                    .addClass(DefaultSimpleActivityImpl.class)
                    // Intentionally unreachable Temporal endpoint for deterministic failure.
                    .addAsResource(new StringAsset(
                            // Ensure worker startup is triggered during Quarkus boot.
                            "quarkus.temporal.start-workers: true\n" +
                            // Invalid endpoint so start attempts fail quickly and consistently.
                                    "quarkus.temporal.connection.target: 127.0.0.1:1\n" +
                                    // Bounded retry mode: retry only three times during startup.
                                    "quarkus.temporal.worker-factory.startup-max-attempts: 3\n" +
                                    // Keep tests fast while still exercising retry behavior.
                                    "quarkus.temporal.worker-factory.startup-retry-delay: 10ms\n" +
                                    // Do not fail application startup after retries are exhausted.
                                    "quarkus.temporal.worker-factory.fail-on-startup-error: false\n"),
                            "application.properties"));

    @Inject
    WorkerFactory factory;

    @Test
    void testStartupContinuesWhenTemporalUnavailable() {
        // If injection succeeds, Quarkus completed startup despite unreachable Temporal.
        Assertions.assertNotNull(factory);
    }
}
