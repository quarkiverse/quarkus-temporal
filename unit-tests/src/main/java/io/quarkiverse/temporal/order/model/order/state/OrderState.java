package io.quarkiverse.temporal.order.model.order.state;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.quarkiverse.temporal.order.model.order.input.OrderInit;
import io.quarkiverse.temporal.order.model.order.input.OrderInput;
import io.quarkiverse.temporal.order.model.order.input.OrderInputItem;

public record OrderState(
        Optional<String> requestId,
        OffsetDateTime dateTime,
        OrderStatus orderStatus,
        String customerId,
        Optional<String> name,
        Optional<String> company,
        Optional<String> address,
        String orderId,
        List<OrderItem> items,
        BigDecimal orderTotal,
        Optional<String> RRN,
        Optional<OrderInput> orderInput) {
    private OffsetDateTime toUTC(long currentTimeMillis) {
        return Instant.ofEpochMilli(currentTimeMillis)
                .atOffset(ZoneOffset.UTC);
    }

    public enum OrderStatus {
        EMPTY,
        INITIALIZED,
        ORDER_RECEIVED,
        VALID,
        INVALID,
        ITEM_SKU_NOTFOUND,
        EXPIRED,
        CANCELED,
        PAYMENT_DECLINED,
        FULFILLMENT
    }

    public static OrderState empty() {
        return new OrderState(Optional.empty(), null, OrderStatus.EMPTY, null, Optional.empty(), Optional.empty(),
                Optional.empty(), null, new ArrayList<>(), BigDecimal.ZERO, Optional.empty(), Optional.empty());
    }

    public OrderState withOrderInit(OrderInit orderInit, long currentTimeMillis) {
        Optional<String> reqId = orderInit.requestId() == null ? Optional.empty() : Optional.of(orderInit.requestId());
        return new OrderState(reqId, toUTC(currentTimeMillis), OrderStatus.INITIALIZED, orderInit.customerId(),
                Optional.empty(), Optional.empty(), Optional.empty(), orderInit.orderId(), new ArrayList<>(), BigDecimal.ZERO,
                Optional.empty(), Optional.empty());
    }

    public OrderState withOrderInput(OrderInput orderInput) {
        ArrayList<OrderItem> orderItems = new ArrayList<>();
        for (OrderInputItem item : orderInput.order().items()) {
            OrderItem orderItem = new OrderItem(item.itemId(), item.quantity(), Optional.empty(), Optional.empty(),
                    Optional.empty());
            orderItems.add(orderItem);
        }
        Optional<String> reqId = orderInput.requestId() == null ? Optional.empty() : Optional.of(orderInput.requestId());
        return new OrderState(reqId, this.dateTime, OrderStatus.ORDER_RECEIVED, orderInput.customerId(), this.name,
                this.company, this.address, orderInput.order().orderId(), orderItems, this.orderTotal, this.RRN,
                Optional.of(orderInput));
    }

    public OrderState withStatus(OrderStatus newStatus) {
        return new OrderState(this.requestId, this.dateTime, newStatus, this.customerId, this.name, this.company, this.address,
                this.orderId, this.items, this.orderTotal, this.RRN, this.orderInput);
    }

    public OrderState withFullEnrichment(OrderStatus newStatus, String newName, String newCompany, String newAddress,
            List<OrderItem> updateItems) {
        BigDecimal calcOrderTotal = updateItems.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.price().isPresent())
                .map(item -> item.price().get().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Optional<String> nName = newName == null ? Optional.empty() : Optional.of(newName);
        Optional<String> nCompany = newCompany == null ? Optional.empty() : Optional.of(newCompany);
        Optional<String> nAddress = newAddress == null ? Optional.empty() : Optional.of(newAddress);
        return new OrderState(this.requestId, this.dateTime, newStatus, this.customerId, nName, nCompany, nAddress, orderId,
                updateItems, calcOrderTotal, this.RRN, this.orderInput);
    }

    public OrderState withValidRRN(String RRN) {
        return new OrderState(this.requestId, this.dateTime, this.orderStatus, this.customerId, this.name, this.company,
                this.address, this.orderId, this.items, this.orderTotal, Optional.of(RRN), this.orderInput);
    }

}
