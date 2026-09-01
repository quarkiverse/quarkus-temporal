package io.quarkiverse.temporal.order.activities;

import io.quarkiverse.temporal.order.model.customer.OrderEntityResponse;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "OrderSupplemental")
public interface OrderSupplementalActivities {

    OrderEntityResponse getOrderEntity(String customerId, String orderId);
}
