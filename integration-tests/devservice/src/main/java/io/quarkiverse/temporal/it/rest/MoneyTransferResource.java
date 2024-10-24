package io.quarkiverse.temporal.it.rest;

import static io.quarkiverse.temporal.Constants.DEFAULT_WORKER_NAME;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import io.quarkiverse.temporal.it.moneyTransfer.defaultWorker.CoreTransactionDetails;
import io.quarkiverse.temporal.it.moneyTransfer.shared.MoneyTransferWorkflow;
import io.quarkiverse.temporal.it.moneyTransfer.shared.TransactionDetails;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

@Path("/money-transfer")
public class MoneyTransferResource {

    @Inject
    WorkflowClient client;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public TransactionDetails transferMoney(@QueryParam("sourceAccountId") String sourceAccountId,
            @QueryParam("destinationAccountId") String destinationAccountId,
            @QueryParam("transactionReferenceId") String transactionReferenceId,
            @QueryParam("amountToTransfer") int amountToTransfer) {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(DEFAULT_WORKER_NAME)
                .setWorkflowId("money-transfer-workflow")
                .build();

        MoneyTransferWorkflow workflow = client.newWorkflowStub(MoneyTransferWorkflow.class, options);
        TransactionDetails transaction = new CoreTransactionDetails(sourceAccountId, destinationAccountId,
                transactionReferenceId, amountToTransfer);
        workflow.transfer(transaction);
        return transaction;
    }
}
