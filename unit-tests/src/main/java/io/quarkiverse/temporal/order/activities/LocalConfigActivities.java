package io.quarkiverse.temporal.order.activities;

import io.quarkiverse.temporal.order.model.workflow.OrderEntityConfig;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface(namePrefix = "OrderLocalConfig")
public interface LocalConfigActivities {
    @ActivityMethod
    OrderEntityConfig getEntityConfig();

}
