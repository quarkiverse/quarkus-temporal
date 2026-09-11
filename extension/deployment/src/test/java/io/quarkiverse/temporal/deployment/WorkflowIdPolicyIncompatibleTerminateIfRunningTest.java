package io.quarkiverse.temporal.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.TemporalInstance;
import io.quarkiverse.temporal.TemporalWorkflowStub;
import io.quarkiverse.temporal.deployment.config.DefaultSimpleWorkflowImpl;
import io.quarkiverse.temporal.deployment.config.SimpleWorkflow;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusUnitTest;

/**
 * TERMINATE_IF_RUNNING cannot be combined with an explicit conflict policy, the Temporal server rejects such a request with an
 * InvalidArgument error.
 */
public class WorkflowIdPolicyIncompatibleTerminateIfRunningTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleWorkflow.class)
                    .addClass(DefaultSimpleWorkflowImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n" +
                                    "quarkus.temporal.workflow.workflow-id-reuse-policy: terminate-if-running\n" +
                                    "quarkus.temporal.workflow.workflow-id-conflict-policy: terminate-existing\n"),
                            "application.properties"));

    @Inject
    @TemporalWorkflowStub
    TemporalInstance<SimpleWorkflow> instance;

    @Test
    public void testIncompatibleWorkflowIdPolicies() {
        ConfigurationException exception = Assertions.assertThrows(ConfigurationException.class,
                () -> instance.workflowId("the-workflow-id"));
        Assertions.assertTrue(
                exception.getMessage().contains("quarkus.temporal.workflow.workflow-id-reuse-policy=TERMINATE_IF_RUNNING"),
                exception.getMessage());
    }
}
