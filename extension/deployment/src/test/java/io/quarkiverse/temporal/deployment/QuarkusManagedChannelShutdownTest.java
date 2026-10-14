package io.quarkiverse.temporal.deployment;

import java.util.function.BooleanSupplier;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.serviceclient.WorkflowServiceStubs;

/**
 * Service stubs on a Quarkus managed channel are shut down on application stop as well.
 */
public class QuarkusManagedChannelShutdownTest {

    // The test class runs in the application class loader, so state is handed to the after-all hook through a JDK type.
    static final String STUBS_SHUTDOWN = "QuarkusManagedChannelShutdownTest.stubsShutdown";
    static final String STUBS_TERMINATED = "QuarkusManagedChannelShutdownTest.stubsTerminated";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(
                            new StringAsset(
                                    "quarkus.temporal.start-workers: false\n" +
                                            "quarkus.temporal.channel-type: quarkus-managed\n" +
                                            "quarkus.temporal.connection.target: customTarget:1234\n"),
                            "application.properties"))
            .setAfterAllCustomizer(() -> {
                Assertions.assertTrue(check(STUBS_SHUTDOWN), "service stubs were not shut down on application stop");
                Assertions.assertTrue(check(STUBS_TERMINATED), "service stubs did not terminate on application stop");
            });

    @Inject
    WorkflowServiceStubs serviceStubs;

    @Test
    public void testStubsRunningWhileApplicationIsUp() {
        WorkflowServiceStubs stubs = ClientProxy.unwrap(serviceStubs);
        Assertions.assertFalse(stubs.isShutdown());
        System.getProperties().put(STUBS_SHUTDOWN, (BooleanSupplier) stubs::isShutdown);
        System.getProperties().put(STUBS_TERMINATED, (BooleanSupplier) stubs::isTerminated);
    }

    static boolean check(String key) {
        BooleanSupplier supplier = (BooleanSupplier) System.getProperties().remove(key);
        Assertions.assertNotNull(supplier, "test method did not run");
        return supplier.getAsBoolean();
    }
}
