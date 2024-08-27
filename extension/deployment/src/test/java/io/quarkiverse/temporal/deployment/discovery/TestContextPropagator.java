package io.quarkiverse.temporal.deployment.discovery;

import java.util.Map;

import jakarta.inject.Singleton;

import io.quarkus.arc.Unremovable;
import io.temporal.api.common.v1.Payload;
import io.temporal.common.context.ContextPropagator;

@Singleton
@Unremovable
public class TestContextPropagator implements ContextPropagator {
    @Override
    public String getName() {
        return this.getClass().getName();
    }

    @Override
    public Map<String, Payload> serializeContext(Object context) {
        return Map.of();
    }

    @Override
    public Object deserializeContext(Map<String, Payload> context) {
        return null;
    }

    @Override
    public Object getCurrentContext() {
        return null;
    }

    @Override
    public void setCurrentContext(Object context) {
    }
}
