package io.quarkiverse.temporal.it.cdi.shared;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface CDIActivity {

    @ActivityMethod
    void cdi();

}
