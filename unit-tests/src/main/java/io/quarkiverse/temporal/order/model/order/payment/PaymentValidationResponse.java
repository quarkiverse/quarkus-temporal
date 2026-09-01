package io.quarkiverse.temporal.order.model.order.payment;

import java.util.Optional;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaymentValidationResponse(boolean isPaymentValid, Optional<String> reason) {
}
