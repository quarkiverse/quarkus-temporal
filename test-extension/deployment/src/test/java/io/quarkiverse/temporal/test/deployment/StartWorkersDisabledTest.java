package io.quarkiverse.temporal.test.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.temporal.worker.WorkerFactory;

class StartWorkersDisabledTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.enable-mock: true\n" +
                                    "quarkus.temporal.start-workers: false\n"),
                            "application.properties"));

    @Inject
    WorkerFactory factory;

    @Test
    public void testStartWorkersDisabled() {
        Assertions.assertFalse(factory.isStarted());
    }

}
