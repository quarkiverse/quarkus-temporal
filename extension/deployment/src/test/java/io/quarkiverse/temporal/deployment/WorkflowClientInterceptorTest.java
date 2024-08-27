package io.quarkiverse.temporal.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.deployment.discovery.TestWorkflowClientInterceptor;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.client.WorkflowClient;

public class WorkflowClientInterceptorTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(TestWorkflowClientInterceptor.class)
                    .addAsResource(new StringAsset("quarkus.temporal.start-workers: false\n"),
                            "application.properties"));

    @Inject
    WorkflowClient client;

    @Test
    public void testInterceptors() {
        Assertions.assertEquals(1, client.getOptions().getInterceptors().length);
    }

}
