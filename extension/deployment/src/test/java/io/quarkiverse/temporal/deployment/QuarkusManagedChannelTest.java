package io.quarkiverse.temporal.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.temporal.serviceclient.WorkflowServiceStubs;

public class QuarkusManagedChannelTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(
                            new StringAsset(
                                    "quarkus.temporal.start-workers: false\n" +
                                            "quarkus.temporal.channel-type: quarkus-managed\n" +
                                            "quarkus.temporal.connection.target: customTarget:1234\n"),
                            "application.properties"));

    @Inject
    WorkflowServiceStubs serviceStubs;

    @Test
    public void testQuarkusManagedChannel() {
        Assertions.assertNull(serviceStubs.getOptions().getTarget());
        Assertions.assertEquals("customTarget:1234", serviceStubs.getOptions().getChannel().authority());
    }
}
