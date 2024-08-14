package io.quarkiverse.temporal.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.deployment.config.NamedSimpleActivityImpl;
import io.quarkiverse.temporal.deployment.config.SimpleActivity;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

public class WorkerNamedDefaultConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleActivity.class)
                    .addClass(NamedSimpleActivityImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n"),
                            "application.properties"));

    @Inject
    WorkerFactory factory;

    @Test
    public void testNamedConfiguration() throws IllegalAccessException {
        // queue name default to worker name
        Worker worker = factory.getWorker("namedWorker");
        Assertions.assertNotNull(worker);
    }
}
