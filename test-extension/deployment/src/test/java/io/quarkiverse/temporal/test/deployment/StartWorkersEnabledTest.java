package io.quarkiverse.temporal.test.deployment;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.worker.WorkerFactory;

@Disabled
class StartWorkersEnabledTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setForcedDependencies(List.of(Dependency.of("io.quarkiverse.temporal", "quarkus-temporal-test", "999-SNAPSHOT")))
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("quarkus.temporal.enable-mock: true\n" +
                            "quarkus.temporal.start-workers: true\n"), "application.properties"));

    @Inject
    WorkerFactory factory;

    @Test
    public void testStartWorker() {
        Assertions.assertTrue(factory.isStarted());
    }

}
