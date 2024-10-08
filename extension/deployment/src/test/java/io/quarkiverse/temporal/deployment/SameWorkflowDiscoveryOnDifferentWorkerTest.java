package io.quarkiverse.temporal.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.deployment.discovery.DefaultSimpleWorkflowImpl;
import io.quarkiverse.temporal.deployment.discovery.NamedSimpleWorkflowImpl;
import io.quarkiverse.temporal.deployment.discovery.SimpleWorkflow;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.worker.WorkerFactory;

public class SameWorkflowDiscoveryOnDifferentWorkerTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleWorkflow.class)
                    .addClass(DefaultSimpleWorkflowImpl.class)
                    .addClass(NamedSimpleWorkflowImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n"),
                            "application.properties"));

    @Inject
    WorkerFactory factory;

    @Test
    public void testSameWorkflowDiscoveryOnSameWorker() {
        // should be called, this deployment is valid:
        // it is legal to have two different implementations of a given workflow on different workers
    }
}
