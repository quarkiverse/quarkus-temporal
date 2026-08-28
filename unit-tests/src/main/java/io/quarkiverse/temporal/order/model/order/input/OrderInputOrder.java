package io.quarkiverse.temporal.order.model.order.input;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OrderInputOrder(
        String orderId,
        List<OrderInputItem> items) {
}
