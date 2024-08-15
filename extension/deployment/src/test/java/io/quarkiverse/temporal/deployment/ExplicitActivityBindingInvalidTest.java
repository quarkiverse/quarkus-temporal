package io.quarkiverse.temporal.deployment;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.deployment.binding.NotAnActivityOrWorkflowImpl;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusUnitTest;

public class ExplicitActivityBindingInvalidTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setExpectedException(ConfigurationException.class)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(NotAnActivityOrWorkflowImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n" +
                                    "quarkus.temporal.worker.namedWorker.activity-classes[0]: io.quarkiverse.temporal.deployment.binding.NotAnActivityImpl"),
                            "application.properties"));

    @Test
    public void testExplicitBinding() {
        Assertions.fail();
    }
}
