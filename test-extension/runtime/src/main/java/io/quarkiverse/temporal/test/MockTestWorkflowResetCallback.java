package io.quarkiverse.temporal.test;

import java.util.Optional;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkiverse.temporal.WorkerRegistrationRegistry;
import io.quarkiverse.temporal.WorkflowClientOptionsSupport;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.callback.QuarkusTestAfterEachCallback;
import io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;
import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestEnvironmentOptions;
import io.temporal.testing.TestWorkflowEnvironment;

/**
 * Gives every {@code @Test} method its own {@link TestWorkflowEnvironment} (and derived
 * {@link WorkflowClient}/{@link io.temporal.worker.WorkerFactory}) instead of the single,
 * shared, leaking instance the extension previously handed out for the whole test JVM run.
 *
 * <p>
 * {@code TestWorkflowEnvironment} and {@code WorkflowClient} are swapped per test via
 * {@link QuarkusMock}. {@code WorkerFactory} cannot use the same mechanism (it's a final SDK
 * class with only a private constructor, so ArC can't generate a client proxy for it) - it is
 * instead {@code @Dependent}-scoped and reads the "currently prepared" environment from
 * {@link MockTestEnvironmentHolder}, which this callback prepares one test ahead: CDI resolves
 * {@code @Dependent} fields at test-construction time, before {@link #beforeEach} can run, so
 * the fresh instance has to already be in the holder by then.
 */
public class MockTestWorkflowResetCallback implements QuarkusTestBeforeEachCallback, QuarkusTestAfterEachCallback {

    private static final Logger log = Logger.getLogger(MockTestWorkflowResetCallback.class);

    private TestWorkflowEnvironment inUseForCurrentTest;

    @Override
    public void beforeEach(QuarkusTestMethodContext context) {
        if (!isMockEnabled()) {
            return;
        }
        // Already prepared - by app boot (the very first test) or by the previous test's
        // afterEach (every test after that).
        TestWorkflowEnvironment current = MockTestEnvironmentHolder.current();
        if (current == null) {
            return;
        }
        QuarkusMock.installMockForType(current, TestWorkflowEnvironment.class);
        QuarkusMock.installMockForType(current.getWorkflowClient(), WorkflowClient.class);
        this.inUseForCurrentTest = current;
    }

    @Override
    public void afterEach(QuarkusTestMethodContext context) {
        if (!isMockEnabled()) {
            return;
        }
        TestWorkflowEnvironment justUsed = this.inUseForCurrentTest;
        this.inUseForCurrentTest = null;
        if (justUsed != null) {
            shutdownQuietly(justUsed);
        }
        prepareNext();
    }

    private void prepareNext() {
        TestEnvironmentOptions options = TestEnvironmentOptions.newBuilder()
                .setWorkflowClientOptions(WorkflowClientOptionsSupport.buildFromCurrentCdi("default", Optional.empty()))
                .build();
        TestWorkflowEnvironment next = TestWorkflowEnvironment.newInstance(options);

        MockTestEnvironmentHolder.set(next);
        WorkerRegistrationRegistry.replayAll();

        if (isStartWorkersEnabled()) {
            next.getWorkerFactory().start();
        }
    }

    private void shutdownQuietly(TestWorkflowEnvironment environment) {
        try {
            environment.getWorkerFactory().shutdown();
        } catch (RuntimeException e) {
            log.debugf(e, "Ignoring failure shutting down worker factory - likely already shut down by the test");
        }
        try {
            environment.close();
        } catch (RuntimeException e) {
            log.debugf(e, "Ignoring failure closing test workflow environment - likely already closed by the test");
        }
    }

    private static boolean isMockEnabled() {
        return ConfigProvider.getConfig()
                .getOptionalValue("quarkus.temporal.enable-mock", Boolean.class)
                .orElse(false);
    }

    private static boolean isStartWorkersEnabled() {
        return ConfigProvider.getConfig()
                .getOptionalValue("quarkus.temporal.start-workers", Boolean.class)
                .orElse(false);
    }
}
