package fr.lavachequicode.temporal.test.plugin.deployment.deployment.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import fr.lavachequicode.temporal.test.plugin.deployment.deployment.worker.CoreTransactionDetails;

@JsonDeserialize(as = CoreTransactionDetails.class)
public interface TransactionDetails {
    String getSourceAccountId();

    String getDestinationAccountId();

    String getTransactionReferenceId();

    int getAmountToTransfer();
}
