package io.quarkiverse.temporal.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.deployment.discovery.LowerPriorityWorkflowClientInterceptor;
import io.quarkiverse.temporal.deployment.discovery.TestWorkflowClientInterceptor;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.client.WorkflowClient;
import io.temporal.common.interceptors.WorkflowClientInterceptor;

public class WorkflowClientInterceptorTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(LowerPriorityWorkflowClientInterceptor.class)
                    .addClass(TestWorkflowClientInterceptor.class)
                    .addAsResource(new StringAsset("quarkus.temporal.start-workers: false\n"),
                            "application.properties"));

    @Inject
    WorkflowClient client;

    @Test
    public void testInterceptors() {
        WorkflowClientInterceptor[] interceptors = client.getOptions().getInterceptors();
        Assertions.assertEquals(2, interceptors.length);
        // interceptors should be in priority order
        Assertions.assertEquals(TestWorkflowClientInterceptor.class, interceptors[0].getClass());
        Assertions.assertEquals(LowerPriorityWorkflowClientInterceptor.class, interceptors[1].getClass());
    }

}
