package io.quarkiverse.temporal.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkiverse.temporal.TemporalWorkflowStub;
import io.quarkiverse.temporal.it.freshness.defaultWorker.FreshnessWorkflow;
import io.quarkus.test.junit.QuarkusTest;
import io.temporal.testing.TestWorkflowEnvironment;

/**
 * Reproduces PR #233's Failure3Test pattern: a field-injected
 * {@code @TemporalWorkflowStub} workflow stub (rather than one built by hand from
 * an injected {@code WorkflowClient}), used across two ordered test methods that
 * each explicitly close the environment.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestWorkflowStubFreshnessIT {

    @Inject
    TestWorkflowEnvironment testEnv;

    @Inject
    @TemporalWorkflowStub
    FreshnessWorkflow workflow;

    @Test
    @Order(1)
    public void firstTestRunsAndClosesTheEnvironment() {
        runAndClose("first");
    }

    @Test
    @Order(2)
    public void secondTestGetsAFreshEnvironment() {
        runAndClose("second");
    }

    private void runAndClose(String input) {
        try {
            assertEquals("pong:" + input, workflow.ping(input));
        } finally {
            testEnv.getWorkerFactory().shutdown();
            testEnv.close();
        }
    }
}
