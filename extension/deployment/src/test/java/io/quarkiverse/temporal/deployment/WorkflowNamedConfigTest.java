package io.quarkiverse.temporal.deployment;

import static io.temporal.api.enums.v1.WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_TERMINATE_EXISTING;
import static io.temporal.api.enums.v1.WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_TERMINATE_IF_RUNNING;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.TemporalWorkflowStub;
import io.quarkiverse.temporal.deployment.config.DefaultSimpleWorkflowImpl;
import io.quarkiverse.temporal.deployment.config.SimpleWorkflow;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;

public class WorkflowNamedConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleWorkflow.class)
                    .addClass(DefaultSimpleWorkflowImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n" +
                                    "quarkus.temporal.workflow.group1.workflow-id-reuse-policy: terminate-if-running\n"
                                    +
                                    "quarkus.temporal.workflow.group1.workflow-id-conflict-policy: terminate-existing\n"
                                    +
                                    "quarkus.temporal.workflow.group1.workflow-run-timeout: 7s\n" +
                                    "quarkus.temporal.workflow.group1.workflow-execution-timeout: 11s\n" +
                                    "quarkus.temporal.workflow.group1.workflow-task-timeout: 13s\n" +
                                    "quarkus.temporal.workflow.group1.start-delay: 17s\n"),
                            "application.properties"));

    @Inject
    @TemporalWorkflowStub(group = "group1")
    SimpleWorkflow workflow;

    @Test
    public void testNamedWorkflowConfiguration() throws IllegalAccessException {
        WorkflowStub workflowStub = WorkflowStub.fromTyped(workflow);
        WorkflowOptions workflowOptions = workflowStub.getOptions().orElse(null);
        Assertions.assertNotNull(workflowOptions);
        Assertions.assertEquals(WORKFLOW_ID_REUSE_POLICY_TERMINATE_IF_RUNNING, workflowOptions.getWorkflowIdReusePolicy());
        Assertions.assertEquals(WORKFLOW_ID_CONFLICT_POLICY_TERMINATE_EXISTING, workflowOptions.getWorkflowIdConflictPolicy());
        Assertions.assertEquals(Duration.of(7, ChronoUnit.SECONDS), workflowOptions.getWorkflowRunTimeout());
        Assertions.assertEquals(Duration.of(11, ChronoUnit.SECONDS), workflowOptions.getWorkflowExecutionTimeout());
        Assertions.assertEquals(Duration.of(13, ChronoUnit.SECONDS), workflowOptions.getWorkflowTaskTimeout());
        Assertions.assertEquals(Duration.of(17, ChronoUnit.SECONDS), workflowOptions.getStartDelay());
    }
}
