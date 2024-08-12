package io.quarkiverse.temporal.deployment;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.deployment.discovery.DefaultSimpleActivityImpl;
import io.quarkiverse.temporal.deployment.discovery.DuplicateDefaultSimpleActivityImpl;
import io.quarkiverse.temporal.deployment.discovery.SimpleActivity;
import io.quarkus.test.QuarkusUnitTest;

public class SameActivityDiscoveryOnSameWorkerTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .assertException(ex -> {
                Assertions.assertEquals(IllegalStateException.class, ex.getClass());
                Assertions.assertEquals(
                        "Activity io.quarkiverse.temporal.deployment.discovery.SimpleActivity has more than one implementor on worker",
                        ex.getMessage());
            })
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleActivity.class)
                    .addClass(DefaultSimpleActivityImpl.class)
                    .addClass(DuplicateDefaultSimpleActivityImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n"),
                            "application.properties"));

    @Test
    public void testSameActivityDiscoveryOnSameWorker() {
        // should not be called, deployment exception should happen first:
        // it's illegal to have two implementations of a given activity on the same worker
        Assertions.fail();
    }
}
