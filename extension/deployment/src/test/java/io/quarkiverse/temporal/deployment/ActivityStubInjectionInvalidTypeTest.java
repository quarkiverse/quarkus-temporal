package io.quarkiverse.temporal.deployment;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.deployment.stubinjection.InvalidTypeWorkflow;
import io.quarkiverse.temporal.deployment.stubinjection.InvalidTypeWorkflowImpl;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusUnitTest;

public class ActivityStubInjectionInvalidTypeTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setExpectedException(ConfigurationException.class)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(InvalidTypeWorkflow.class)
                    .addClass(InvalidTypeWorkflowImpl.class)
                    .addAsResource(new StringAsset("quarkus.temporal.start-workers: false"), "application.properties"));

    @Test
    public void testInvalidInjectionPointType() {
        Assertions.fail();
    }
}
