package io.quarkiverse.temporal.order.activities;

import java.util.List;

import io.quarkiverse.temporal.order.model.order.state.OrderItem;
import io.quarkiverse.temporal.order.model.order.state.OrderState;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "OrderEnrichment")
public interface OrderEnrichmentActivities {

    List<OrderItem> enrichOrderItems(OrderState orderState);
}
