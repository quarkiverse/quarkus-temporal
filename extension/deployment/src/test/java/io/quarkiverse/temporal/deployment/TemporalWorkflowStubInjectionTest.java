package io.quarkiverse.temporal.deployment;

import static io.quarkiverse.temporal.Constants.DEFAULT_WORKER_NAME;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.TemporalWorkflowStub;
import io.quarkiverse.temporal.deployment.stub.SimpleWorkflow;
import io.quarkiverse.temporal.deployment.stub.UnnamedSimpleWorkflowImpl;
import io.quarkus.test.QuarkusUnitTest;

public class TemporalWorkflowStubInjectionTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleWorkflow.class)
                    .addClass(UnnamedSimpleWorkflowImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n"),
                            "application.properties"));

    @Inject
    @TemporalWorkflowStub(worker = DEFAULT_WORKER_NAME)
    SimpleWorkflow workflow;

    @Test
    public void testUnnamedWorkerWorkflowStubInjection() {
        Assertions.assertNotNull(workflow);
    }
}
