package io.quarkiverse.temporal.deployment;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.deployment.discovery.DefaultSimpleWorkflowImpl;
import io.quarkiverse.temporal.deployment.discovery.DuplicateDefaultSimpleWorkflowImpl;
import io.quarkiverse.temporal.deployment.discovery.SimpleWorkflow;
import io.quarkus.test.QuarkusUnitTest;

public class SameWorkflowDiscoveryOnSameWorkerTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .assertException(ex -> {
                Assertions.assertEquals(IllegalStateException.class, ex.getClass());
                Assertions.assertEquals(
                        "Workflow io.quarkiverse.temporal.deployment.discovery.SimpleWorkflow has more than one implementor on worker",
                        ex.getMessage());
            })
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleWorkflow.class)
                    .addClass(DefaultSimpleWorkflowImpl.class)
                    .addClass(DuplicateDefaultSimpleWorkflowImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n"),
                            "application.properties"));

    @Test
    public void testSameWorkflowDiscoveryOnSameWorker() {
        // should not be called, deployment exception should happen first:
        // it's illegal to have two implementations of a given workflow on the same worker
        Assertions.fail();
    }
}
