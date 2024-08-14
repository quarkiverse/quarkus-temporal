package io.quarkiverse.temporal.deployment;

import io.quarkus.test.QuarkusUnitTest;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.WorkerFactory;
import jakarta.inject.Inject;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CustomClientTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(CustomClientProducer.class)
                    .addAsResource(new StringAsset("quarkus.temporal.enable-mock: false\n"), "application.properties"));


    @Inject
    WorkflowClient client;
    @Inject
    WorkerFactory factory;

    @Test
    public void testStartWorker() {
        Assertions.assertEquals("Custom Client", client.getOptions().getIdentity());
        Assertions.assertEquals("Custom Client", factory.getWorkflowClient().getOptions().getIdentity());

    }
}
