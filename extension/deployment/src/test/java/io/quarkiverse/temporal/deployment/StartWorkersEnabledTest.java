package io.quarkiverse.temporal.deployment;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.WorkerFactory;

class StartWorkersEnabledTest {

    static TestWorkflowEnvironment testWorkflowEnvironment = TestWorkflowEnvironment.newInstance();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset("quarkus.temporal.start-workers: true\n"), "application.properties"));

    @Inject
    WorkerFactory factory;

    @Test
    public void testStartWorker() {
        Assertions.assertTrue(factory.isStarted());
    }

    public static class Producers {

        @Produces
        WorkflowClient client() {
            return testWorkflowEnvironment.getWorkflowClient();
        }
    }

}
