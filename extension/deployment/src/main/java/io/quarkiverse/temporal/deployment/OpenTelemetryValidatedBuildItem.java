package io.quarkiverse.temporal.deployment;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Build item to signify OpenTelemetry settings have been validated.
 */
public final class OpenTelemetryValidatedBuildItem extends SimpleBuildItem {
    private boolean enabled = false;

    public OpenTelemetryValidatedBuildItem(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return this.enabled;
    }
}