package io.quarkiverse.temporal.order.model.customer;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaymentInfo(
        String requestId,
        String customerId,
        String orderId,
        String RRN) {
}