package io.quarkiverse.temporal.deployment.discovery;

import io.quarkiverse.temporal.ActivityImpl;

@ActivityImpl(workers = "namedWorker")
public class NamedSimpleActivityImpl implements SimpleActivity {
    @Override
    public void withdraw() {
    }
}
