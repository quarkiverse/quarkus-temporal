package io.quarkiverse.temporal.deployment;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;

public class CustomClientTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(CustomClientProducer.class)
                    .addAsResource(new StringAsset("quarkus.temporal.start-workers: false\n"), "application.properties"));

    @Inject
    WorkflowClient client;
    @Inject
    WorkerFactory factory;

    @Test
    public void testCustomClient() {
        Assertions.assertEquals("Custom Client", client.getOptions().getIdentity());
        Assertions.assertEquals("Custom Client", factory.getWorkflowClient().getOptions().getIdentity());

    }

    public static class CustomClientProducer {

        @Produces
        WorkflowClient client() {
            WorkflowServiceStubs workflowServiceStubs = WorkflowServiceStubs
                    .newServiceStubs(WorkflowServiceStubsOptions.getDefaultInstance());
            return WorkflowClient.newInstance(workflowServiceStubs, WorkflowClientOptions.newBuilder()
                    .setIdentity("Custom Client")
                    .build());
        }
    }
}
