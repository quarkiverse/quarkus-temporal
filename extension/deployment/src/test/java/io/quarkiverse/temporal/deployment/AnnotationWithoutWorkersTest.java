package io.quarkiverse.temporal.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.Constants;
import io.quarkiverse.temporal.deployment.defaultworker.SimpleActivity;
import io.quarkiverse.temporal.deployment.defaultworker.SimpleActivityImpl;
import io.quarkiverse.temporal.deployment.defaultworker.SimpleWorkflow;
import io.quarkiverse.temporal.deployment.defaultworker.SimpleWorkflowImpl;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.worker.WorkerFactory;

/**
 * {@code @TemporalWorkflow} and {@code @TemporalActivity} without {@code workers} bind to the default worker.
 */
public class AnnotationWithoutWorkersTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleWorkflow.class)
                    .addClass(SimpleWorkflowImpl.class)
                    .addClass(SimpleActivity.class)
                    .addClass(SimpleActivityImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n"),
                            "application.properties"));

    @Inject
    WorkerFactory factory;

    @Test
    public void testDefaultWorker() {
        Assertions.assertNotNull(factory.tryGetWorker(Constants.DEFAULT_WORKER_NAME));
    }
}
