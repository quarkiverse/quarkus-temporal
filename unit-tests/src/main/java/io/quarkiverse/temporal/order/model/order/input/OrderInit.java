package io.quarkiverse.temporal.order.model.order.input;

public record OrderInit(
        String requestId,
        String customerId,
        String orderId) {
}
