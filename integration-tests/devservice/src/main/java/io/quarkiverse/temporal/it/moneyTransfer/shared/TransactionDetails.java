package io.quarkiverse.temporal.it.moneyTransfer.shared;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.quarkiverse.temporal.it.moneyTransfer.defaultWorker.CoreTransactionDetails;

@JsonDeserialize(as = CoreTransactionDetails.class)
public interface TransactionDetails {
    String getSourceAccountId();

    String getDestinationAccountId();

    String getTransactionReferenceId();

    int getAmountToTransfer();
}
