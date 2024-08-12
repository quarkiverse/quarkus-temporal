package io.quarkiverse.temporal.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.deployment.discovery.DefaultSimpleActivityImpl;
import io.quarkiverse.temporal.deployment.discovery.SimpleActivity;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

public class ClientConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleActivity.class)
                    .addClass(DefaultSimpleActivityImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n" +
                                    "quarkus.temporal.identity: customIdentity\n" +
                                    "quarkus.temporal.namespace: customNamespace\n" +
                                    "quarkus.temporal.connection.target: customTarget:1234\n" +
                                    "quarkus.temporal.connection.enable-https: true\n"),
                            "application.properties"));

    @Inject
    WorkflowClient client;

    @Test
    public void testClientConfiguration() throws IllegalAccessException {
        WorkflowClientOptions options = client.getOptions();
        Assertions.assertNotNull(options);
        Assertions.assertEquals("customIdentity", options.getIdentity());
        Assertions.assertEquals("customNamespace", options.getNamespace());
    }

    @Test
    public void testWorkflowServiceStubsConfiguration() throws IllegalAccessException {
        WorkflowServiceStubsOptions options = client.getWorkflowServiceStubs().getOptions();
        Assertions.assertEquals("customTarget:1234", options.getTarget());
        Assertions.assertTrue(options.getEnableHttps());
    }
}
