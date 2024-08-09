package fr.lavachequicode.temporal.app.shared;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import fr.lavachequicode.temporal.app.worker.CoreTransactionDetails;

@JsonDeserialize(as = CoreTransactionDetails.class)
public interface TransactionDetails {
    String getSourceAccountId();

    String getDestinationAccountId();

    String getTransactionReferenceId();

    int getAmountToTransfer();
}
