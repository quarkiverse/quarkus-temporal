package io.quarkiverse.temporal.deployment.config;

import io.quarkiverse.temporal.ActivityImpl;
import io.quarkiverse.temporal.deployment.discovery.SimpleActivity;

@ActivityImpl(workers = "namedWorker")
public class NamedSimpleActivityImpl implements SimpleActivity {
    @Override
    public void withdraw() {
    }
}
