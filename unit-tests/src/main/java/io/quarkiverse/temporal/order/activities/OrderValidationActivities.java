package io.quarkiverse.temporal.order.activities;

import io.quarkiverse.temporal.order.model.customer.OrderValidationResponse;
import io.quarkiverse.temporal.order.model.order.input.OrderInput;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "OrderValidation")
public interface OrderValidationActivities {

    OrderValidationResponse validateOrder(OrderInput orderInput);
}
