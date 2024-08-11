package io.quarkiverse.temporal.it;

import java.util.concurrent.TimeoutException;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.temporal.it.defaultWorker.CoreTransactionDetails;
import io.quarkiverse.temporal.it.shared.MoneyTransferWorkflow;
import io.quarkiverse.temporal.it.shared.TransactionDetails;
import io.quarkus.test.junit.QuarkusTest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

@QuarkusTest
public class TemporalIT {

    @Inject
    WorkflowClient client;

    @Test
    public void testRunWorkflowOnDefaultWorker() throws TimeoutException {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue("<default>")
                .setWorkflowId("money-transfer-workflow")
                .build();

        MoneyTransferWorkflow workflow = client.newWorkflowStub(MoneyTransferWorkflow.class, options);
        TransactionDetails transaction = new CoreTransactionDetails("249020073", "152354872", "57c65dea-e57e-4a0a", 68);
        workflow.transfer(transaction);
    }

    @Test
    public void testRunWorkflowOnNamedWorker() throws TimeoutException {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue("namedWorker")
                .setWorkflowId("money-transfer-workflow")
                .build();

        MoneyTransferWorkflow workflow = client.newWorkflowStub(MoneyTransferWorkflow.class, options);
        TransactionDetails transaction = new CoreTransactionDetails("249020073", "152354872", "57c65dea-e57e-4a0a", 68);
        workflow.transfer(transaction);
    }
}
