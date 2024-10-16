package io.quarkiverse.temporal.it;

import static io.quarkiverse.temporal.Constants.DEFAULT_WORKER_NAME;

import java.util.concurrent.TimeoutException;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.temporal.it.cdi.shared.CDIWorkflow;
import io.quarkus.test.junit.QuarkusTest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

@QuarkusTest
public class CDIActivityIT {

    @Inject
    WorkflowClient client;

    @Test
    public void testCDIInActivityOnDefaultWorker() throws TimeoutException {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(DEFAULT_WORKER_NAME)
                .setWorkflowId("cdi-workflow")
                .build();

        CDIWorkflow workflow = client.newWorkflowStub(CDIWorkflow.class, options);
        workflow.cdi();
    }

    @Test
    public void testCDIInActivityOnNamedWorker() throws TimeoutException {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue("namedWorker")
                .setWorkflowId("cdi-workflow")
                .build();

        CDIWorkflow workflow = client.newWorkflowStub(CDIWorkflow.class, options);
        workflow.cdi();
    }
}
