package io.quarkiverse.temporal.order.model.customer;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OrderEntityResponse(
        String orderId,
        String customerId,
        String name,
        String company,
        String address) {
}
