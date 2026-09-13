package io.quarkiverse.temporal.deployment;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.deployment.stubinjection.InjectedActivity;
import io.quarkiverse.temporal.deployment.stubinjection.NotAWorkflowWithStub;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusUnitTest;

public class ActivityStubInjectionNotAWorkflowTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setExpectedException(ConfigurationException.class)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(InjectedActivity.class)
                    .addClass(NotAWorkflowWithStub.class)
                    .addAsResource(new StringAsset("quarkus.temporal.start-workers: false"), "application.properties"));

    @Test
    public void testInjectionPointOutsideWorkflow() {
        Assertions.fail();
    }
}
