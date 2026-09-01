package io.quarkiverse.temporal.it;

import static io.quarkiverse.temporal.Constants.DEFAULT_WORKER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkiverse.temporal.it.freshness.FreshnessClientHolder;
import io.quarkiverse.temporal.it.freshness.defaultWorker.FreshnessWorkflow;
import io.quarkus.test.junit.QuarkusTest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TestWorkflowEnvironmentFreshnessIT {

    @Inject
    TestWorkflowEnvironment testEnv;

    @Inject
    FreshnessClientHolder applicationBean;

    @Test
    @Order(1)
    public void firstTestRunsAndClosesTheEnvironment() {
        runAndClose("first");
    }

    @Test
    @Order(2)
    public void secondTestGetsAFreshEnvironment() {
        // Before the fix, this method received the same, already-closed
        // TestWorkflowEnvironment as the first test, and testEnv.getWorkerFactory()
        // below would already be shut down.
        runAndClose("second");
    }

    private void runAndClose(String input) {
        // Uses the client injected into an application-code-style CDI bean (not the test
        // class's own field) to prove the fix isn't limited to the test class's fields.
        // If this client were stale, newWorkflowStub/ping below would fail or hang against
        // a different (or already-closed) in-memory environment.
        WorkflowClient current = applicationBean.get();
        FreshnessWorkflow workflow = current.newWorkflowStub(FreshnessWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(DEFAULT_WORKER_NAME).build());
        try {
            assertEquals("pong:" + input, workflow.ping(input));
        } finally {
            testEnv.getWorkerFactory().shutdown();
            testEnv.close();
        }
    }
}
