package io.quarkiverse.temporal.deployment.binding;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface SimpleActivity {
    @ActivityMethod
    void withdraw();

}
