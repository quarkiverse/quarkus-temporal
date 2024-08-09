package io.quarkiverse.temporal.test.deployment.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.quarkiverse.temporal.test.deployment.worker.CoreTransactionDetails;

@JsonDeserialize(as = CoreTransactionDetails.class)
public interface TransactionDetails {
    String getSourceAccountId();

    String getDestinationAccountId();

    String getTransactionReferenceId();

    int getAmountToTransfer();
}
