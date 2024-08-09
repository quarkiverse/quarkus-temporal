package io.quarkiverse.temporal.test.deployment;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.test.deployment.api.AccountActivity;
import io.quarkiverse.temporal.test.deployment.api.MoneyTransferWorkflow;
import io.quarkiverse.temporal.test.deployment.api.TransactionDetails;
import io.quarkiverse.temporal.test.deployment.worker.AccountActivityImpl;
import io.quarkiverse.temporal.test.deployment.worker.CoreTransactionDetails;
import io.quarkiverse.temporal.test.deployment.worker.MoneyTransferWorkflowImpl;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

class TemporalWorkerTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(AccountActivity.class)
                    .addClass(MoneyTransferWorkflow.class)
                    .addClass(AccountActivityImpl.class)
                    .addClass(TransactionDetails.class)
                    .addClass(CoreTransactionDetails.class)
                    .addClass(MoneyTransferWorkflowImpl.class)
                    .addAsResource(new StringAsset("quarkus.temporal.enable-mock: true\n"), "application.properties"));

    @Inject
    WorkflowClient client;

    private static final SecureRandom random;

    static {
        // Seed the random number generator with nano date
        random = new SecureRandom();
        random.setSeed(Instant.now().getNano());
    }

    public static String randomAccountIdentifier() {
        return IntStream.range(0, 9)
                .mapToObj(i -> String.valueOf(random.nextInt(10)))
                .collect(Collectors.joining());
    }

    @Test
    public void testWorker() {
        // Workflow options configure  Workflow stubs.
        // A WorkflowId prevents duplicate instances, which are removed.
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue("MONEY_TRANSFER_TASK_QUEUE")
                .setWorkflowId("money-transfer-workflow")
                .build();

        // WorkflowStubs enable calls to methods as if the Workflow object is local
        // but actually perform a gRPC call to the Temporal Service.
        MoneyTransferWorkflow workflow = client.newWorkflowStub(MoneyTransferWorkflow.class, options);

        // Configure the details for this money transfer request
        String referenceId = UUID.randomUUID().toString().substring(0, 18);
        String fromAccount = randomAccountIdentifier();
        String toAccount = randomAccountIdentifier();
        int amountToTransfer = ThreadLocalRandom.current().nextInt(15, 75);
        TransactionDetails transaction = new CoreTransactionDetails(fromAccount, toAccount, referenceId, amountToTransfer);

        // Perform asynchronous execution.
        // This process exits after making this call and printing details.
        WorkflowExecution we = WorkflowClient.start(workflow::transfer, transaction);

        System.out.printf("\nMONEY TRANSFER PROJECT\n\n");
        System.out.printf("Initiating transfer of $%d from [Account %s] to [Account %s].\n\n",
                amountToTransfer, fromAccount, toAccount);
        System.out.printf("[WorkflowID: %s]\n[RunID: %s]\n[Transaction Reference: %s]\n\n", we.getWorkflowId(), we.getRunId(),
                referenceId);
    }

}
