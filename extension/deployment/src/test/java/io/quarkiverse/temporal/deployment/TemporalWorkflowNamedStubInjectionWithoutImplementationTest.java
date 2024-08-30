package io.quarkiverse.temporal.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.TemporalWorkflowStub;
import io.quarkiverse.temporal.deployment.stub.NamedSimpleWorkflow;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;

public class TemporalWorkflowNamedStubInjectionWithoutImplementationTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(NamedSimpleWorkflow.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n"),
                            "application.properties"));

    @Inject
    @TemporalWorkflowStub
    NamedSimpleWorkflow workflow;

    @Test
    public void testUnnamedWorkerWorkflowStubInjection() {
        Assertions.assertNotNull(workflow);
        WorkflowStub workflowStub = WorkflowStub.fromTyped(workflow);
        WorkflowOptions workflowOptions = workflowStub.getOptions().orElse(null);
        Assertions.assertNotNull(workflowOptions);
        Assertions.assertEquals("namedWorker", workflowOptions.getTaskQueue());
    }
}