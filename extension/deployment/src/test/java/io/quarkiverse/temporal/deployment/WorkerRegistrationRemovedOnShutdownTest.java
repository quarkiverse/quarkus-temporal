package io.quarkiverse.temporal.deployment;

import java.util.function.IntSupplier;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.WorkerRegistrationRegistry;
import io.quarkiverse.temporal.deployment.discovery.DefaultSimpleWorkflowImpl;
import io.quarkiverse.temporal.deployment.discovery.SimpleWorkflow;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Worker registrations recorded at startup are removed when the application stops, so a stopped application is
 * neither replayed nor kept reachable through the static registry.
 */
public class WorkerRegistrationRemovedOnShutdownTest {

    // The test class runs in the application class loader, so state is handed to the after-all hook through a JDK type.
    static final String REGISTRATIONS = "WorkerRegistrationRemovedOnShutdownTest.registrations";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleWorkflow.class)
                    .addClass(DefaultSimpleWorkflowImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n"),
                            "application.properties"))
            .setAfterAllCustomizer(() -> {
                IntSupplier registrations = (IntSupplier) System.getProperties().remove(REGISTRATIONS);
                Assertions.assertNotNull(registrations, "test method did not run");
                Assertions.assertEquals(0, registrations.getAsInt(),
                        "worker registrations were not removed on application stop");
            });

    @Test
    public void testRegistrationRecordedWhileApplicationIsUp() {
        Assertions.assertEquals(1, WorkerRegistrationRegistry.size());
        System.getProperties().put(REGISTRATIONS, (IntSupplier) WorkerRegistrationRegistry::size);
    }
}
