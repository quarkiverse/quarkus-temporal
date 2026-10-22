package io.quarkiverse.temporal.it;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkiverse.temporal.test.MockTestEnvironmentHolder;
import io.quarkus.test.junit.QuarkusTest;
import io.temporal.testing.TestWorkflowEnvironment;

/**
 * Regression test: the environment a test used must be shut down once the next test starts.
 *
 * The beforeEach/afterEach callbacks are loaded through separate ServiceLoader calls, so the
 * callback can't carry state between them in instance fields. Before the fix the shutdown
 * never ran, and every test in a suite left a started {@link TestWorkflowEnvironment} behind -
 * pollers for every registered task queue included - until the JVM ran out of native threads.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestWorkflowEnvironmentShutdownIT {

    static volatile TestWorkflowEnvironment firstTestEnvironment;

    @Test
    @Order(1)
    public void firstTestRecordsItsEnvironment() {
        firstTestEnvironment = MockTestEnvironmentHolder.current();
        assertNotNull(firstTestEnvironment);
        assertTrue(firstTestEnvironment.getWorkerFactory().isStarted());
    }

    @Test
    @Order(2)
    public void nextTestRunsAgainstFreshEnvironmentAndPreviousOneIsShutDown() {
        assertNotNull(firstTestEnvironment);

        TestWorkflowEnvironment current = MockTestEnvironmentHolder.current();
        assertNotNull(current);
        assertNotSame(firstTestEnvironment, current);
        assertTrue(current.getWorkerFactory().isStarted());

        // The environment the first test used must have been shut down, pollers and all,
        // instead of leaking until the JVM ran out of native threads.
        assertTrue(firstTestEnvironment.getWorkerFactory().isTerminated());
    }
}
