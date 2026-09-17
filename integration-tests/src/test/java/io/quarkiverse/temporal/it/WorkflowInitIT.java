package io.quarkiverse.temporal.it;

import static io.quarkiverse.temporal.Constants.DEFAULT_WORKER_NAME;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.temporal.it.workflowInit.GreetingWorkflow;
import io.quarkus.test.junit.QuarkusTest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

@QuarkusTest
public class WorkflowInitIT {

    @Inject
    WorkflowClient client;

    @Test
    public void testWorkflowInitConstructorWithInjectedActivityStub() {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(DEFAULT_WORKER_NAME)
                .setWorkflowId("greeting-workflow")
                .build();

        GreetingWorkflow workflow = client.newWorkflowStub(GreetingWorkflow.class, options);
        Assertions.assertEquals("Hello Temporal", workflow.greet("Temporal"));
    }
}
