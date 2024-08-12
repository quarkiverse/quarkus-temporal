package io.quarkiverse.temporal.deployment.discovery;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface SimpleActivity {
    @ActivityMethod
    void withdraw();

}
