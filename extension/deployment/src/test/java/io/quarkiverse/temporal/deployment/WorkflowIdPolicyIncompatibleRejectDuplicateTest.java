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
 * REJECT_DUPLICATE cannot be combined with a TERMINATE_EXISTING conflict policy, the Temporal server rejects such a request
 * with an InvalidArgument error.
 */
public class WorkflowIdPolicyIncompatibleRejectDuplicateTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleWorkflow.class)
                    .addClass(DefaultSimpleWorkflowImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n" +
                                    "quarkus.temporal.workflow.group1.workflow-id-reuse-policy: reject-duplicate\n" +
                                    "quarkus.temporal.workflow.group1.workflow-id-conflict-policy: terminate-existing\n"),
                            "application.properties"));

    @Inject
    @TemporalWorkflowStub(group = "group1")
    TemporalInstance<SimpleWorkflow> instance;

    @Test
    public void testIncompatibleWorkflowIdPolicies() {
        ConfigurationException exception = Assertions.assertThrows(ConfigurationException.class,
                () -> instance.workflowId("the-workflow-id"));
        Assertions.assertTrue(
                exception.getMessage()
                        .contains("quarkus.temporal.workflow.group1.workflow-id-reuse-policy=REJECT_DUPLICATE"),
                exception.getMessage());
    }
}
