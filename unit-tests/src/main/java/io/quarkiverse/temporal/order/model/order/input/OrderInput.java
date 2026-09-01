package io.quarkiverse.temporal.order.model.order.input;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OrderInput(
        String requestId,
        String customerId,
        OrderInputOrder order) {
}
