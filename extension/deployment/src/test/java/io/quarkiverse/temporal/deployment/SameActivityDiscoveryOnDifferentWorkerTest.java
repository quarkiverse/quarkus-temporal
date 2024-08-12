package io.quarkiverse.temporal.deployment;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.deployment.discovery.DefaultSimpleActivityImpl;
import io.quarkiverse.temporal.deployment.discovery.NamedSimpleActivityImpl;
import io.quarkiverse.temporal.deployment.discovery.SimpleActivity;
import io.quarkus.test.QuarkusUnitTest;

public class SameActivityDiscoveryOnDifferentWorkerTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleActivity.class)
                    .addClass(DefaultSimpleActivityImpl.class)
                    .addClass(NamedSimpleActivityImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n"),
                            "application.properties"));

    @Test
    public void testSameActivityDiscoveryOnSameWorker() {
        // should be called, this deployment is valid:
        // it is legal to have two different implementations of a given activity on different workers
    }
}
