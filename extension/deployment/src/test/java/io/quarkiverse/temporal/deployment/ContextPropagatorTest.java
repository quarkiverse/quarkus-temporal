package io.quarkiverse.temporal.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.deployment.discovery.TestContextPropagator;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.client.WorkflowClient;

public class ContextPropagatorTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(TestContextPropagator.class)
                    .addAsResource(new StringAsset("quarkus.temporal.start-workers: false\n"),
                            "application.properties"));

    @Inject
    WorkflowClient client;

    @Test
    public void testCustomClient() {
        Assertions.assertEquals(1, client.getOptions().getContextPropagators().size());
    }

}
