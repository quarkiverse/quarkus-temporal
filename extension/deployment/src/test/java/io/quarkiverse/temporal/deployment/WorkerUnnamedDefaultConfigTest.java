package io.quarkiverse.temporal.deployment;

import static io.quarkiverse.temporal.Constants.DEFAULT_WORKER_NAME;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.deployment.config.DefaultSimpleActivityImpl;
import io.quarkiverse.temporal.deployment.config.SimpleActivity;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

public class WorkerUnnamedDefaultConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleActivity.class)
                    .addClass(DefaultSimpleActivityImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n"),
                            "application.properties"));

    @Inject
    WorkerFactory factory;

    @Test
    public void testUnamedDefaultConfiguration() throws IllegalAccessException {
        // queue name default to worker name
        Worker worker = factory.getWorker(DEFAULT_WORKER_NAME);
        Assertions.assertNotNull(worker);
    }
}
