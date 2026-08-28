package io.quarkiverse.temporal.order.model.order.payment;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaymentValidationRequest(
        String customerId,
        String rrn,
        long amountCents,
        Metadata metadata) {
}