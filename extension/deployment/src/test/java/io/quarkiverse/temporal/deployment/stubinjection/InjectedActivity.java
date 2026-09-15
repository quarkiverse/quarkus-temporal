package io.quarkiverse.temporal.deployment.stubinjection;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface InjectedActivity {

    @ActivityMethod
    void run();
}
