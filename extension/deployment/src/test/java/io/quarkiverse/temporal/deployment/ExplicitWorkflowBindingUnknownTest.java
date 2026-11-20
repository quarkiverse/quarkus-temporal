package io.quarkiverse.temporal.deployment;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusExtensionTest;

public class ExplicitWorkflowBindingUnknownTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setExpectedException(ConfigurationException.class)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n" +
                                    "quarkus.temporal.worker.namedWorker.workflow-classes[0]: io.quarkiverse.temporal.deployment.binding.NonExistentWorkflowImpl"),
                            "application.properties"));

    @Test
    public void testExplicitBinding() {
        Assertions.fail();
    }
}
