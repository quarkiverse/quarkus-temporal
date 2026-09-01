package io.quarkiverse.temporal.order.model.order.state;

import java.math.BigDecimal;
import java.util.Optional;

public record OrderItem(
        String itemId,
        int quantity,
        Optional<String> SKU,
        Optional<String> brand,
        Optional<BigDecimal> price) {
    public OrderItem withEnrichment(String sku, String brand, BigDecimal price) {
        return new OrderItem(this.itemId, this.quantity, Optional.of(sku), Optional.of(brand), Optional.of(price));
    }
}
