package io.quarkiverse.temporal.deployment;

import static io.temporal.api.enums.v1.WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_TERMINATE_IF_RUNNING;

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

/**
 * The workflow id conflict policy must be left unset when it is not configured. The Temporal server only migrates the
 * deprecated {@code TERMINATE_IF_RUNNING} reuse policy when no conflict policy is sent, and rejects the request otherwise.
 */
public class WorkflowIdConflictPolicyUnsetTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleWorkflow.class)
                    .addClass(DefaultSimpleWorkflowImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n" +
                                    "quarkus.temporal.workflow.workflow-id-reuse-policy: terminate-if-running\n"),
                            "application.properties"));

    @Inject
    @TemporalWorkflowStub
    SimpleWorkflow workflow;

    @Test
    public void testConflictPolicyIsNotSetWhenNotConfigured() {
        WorkflowOptions workflowOptions = WorkflowStub.fromTyped(workflow).getOptions().orElse(null);
        Assertions.assertNotNull(workflowOptions);
        Assertions.assertEquals(WORKFLOW_ID_REUSE_POLICY_TERMINATE_IF_RUNNING, workflowOptions.getWorkflowIdReusePolicy());
        Assertions.assertNull(workflowOptions.getWorkflowIdConflictPolicy(),
                "the conflict policy must stay unset so that the server can apply its own default");
    }
}
