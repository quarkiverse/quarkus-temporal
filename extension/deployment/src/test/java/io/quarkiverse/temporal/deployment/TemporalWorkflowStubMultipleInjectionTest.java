package io.quarkiverse.temporal.deployment;

import static io.quarkiverse.temporal.Constants.DEFAULT_WORKER_NAME;

import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.TemporalWorkflowStub;
import io.quarkiverse.temporal.deployment.stub.NamedSimpleWorkflowImpl;
import io.quarkiverse.temporal.deployment.stub.SimpleWorkflow;
import io.quarkiverse.temporal.deployment.stub.UnnamedSimpleWorkflowImpl;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;

public class TemporalWorkflowStubMultipleInjectionTest {

    public static TestWorkflowEnvironment testWorkflowEnvironment = TestWorkflowEnvironment.newInstance();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleWorkflow.class)
                    .addClass(UnnamedSimpleWorkflowImpl.class)
                    .addClass(NamedSimpleWorkflowImpl.class)
                    .addAsResource(
                            new StringAsset("\n"),
                            "application.properties"));

    @Inject
    WorkflowClient workflowClient;

    @Inject
    @TemporalWorkflowStub(worker = DEFAULT_WORKER_NAME)
    SimpleWorkflow defaultWorkflow;

    @Inject
    @TemporalWorkflowStub(worker = DEFAULT_WORKER_NAME, workflowId = "workflowIdForSimpleWorkflow")
    SimpleWorkflow defaultWorkflowWithWorkflowId;

    @Inject
    @TemporalWorkflowStub(worker = DEFAULT_WORKER_NAME, workflowId = "otherWorkflowIdForSimpleWorkflow")
    SimpleWorkflow defaultWorkflowWithOtherWorkflowId;

    @Inject
    @TemporalWorkflowStub(worker = "namedWorker")
    SimpleWorkflow namedWorkflow;

    @Test
    public void testUnnamedWorkerWorkflowStubInjection() {
        Assertions.assertNotNull(defaultWorkflow);
        Assertions.assertNotNull(namedWorkflow);
        WorkflowExecution execution = WorkflowClient.start(defaultWorkflowWithWorkflowId::transfer);
        Assertions.assertEquals("workflowIdForSimpleWorkflow", execution.getWorkflowId());
        WorkflowExecution otherExecution = WorkflowClient.start(defaultWorkflowWithOtherWorkflowId::transfer);
        Assertions.assertEquals("otherWorkflowIdForSimpleWorkflow", otherExecution.getWorkflowId());
        SimpleWorkflow dynamicWorkflow = CDI.current()
                .select(SimpleWorkflow.class, new TemporalWorkflowStub.Literal(DEFAULT_WORKER_NAME, "dynamicWorkflowId")).get();
        WorkflowExecution dynamicExecution = WorkflowClient.start(dynamicWorkflow::transfer);
        Assertions.assertEquals("dynamicWorkflowId", dynamicExecution.getWorkflowId());

    }

    public static class Producers {
        @Produces
        WorkflowClient client() {
            return testWorkflowEnvironment.getWorkflowClient();
        }
    }
}
