package io.quarkiverse.temporal.deployment;

import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.TemporalWorkflowStub;
import io.quarkiverse.temporal.deployment.stub.MultipleWorkerWorkflowImpl;
import io.quarkiverse.temporal.deployment.stub.SimpleWorkflow;
import io.quarkus.test.QuarkusUnitTest;

public class TemporalWorkflowAmbiguousStubInjectionTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setExpectedException(UnsatisfiedResolutionException.class)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleWorkflow.class)
                    .addClass(MultipleWorkerWorkflowImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n"),
                            "application.properties"));

    @Inject
    @TemporalWorkflowStub()
    SimpleWorkflow workflow;

    @Test
    public void testNonExistentWorkflowStubInjection() {
        // since the workflow is associated with multiple workers, the worker must be explicitly referenced in the qualifier
        Assertions.fail();
    }
}
