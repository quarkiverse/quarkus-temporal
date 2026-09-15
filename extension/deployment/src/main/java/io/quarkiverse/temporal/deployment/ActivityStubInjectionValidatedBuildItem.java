package io.quarkiverse.temporal.deployment;

import io.quarkus.builder.item.EmptyBuildItem;

/**
 * Marker build item produced once all {@code @TemporalActivityStub} injection points have been validated.
 */
public final class ActivityStubInjectionValidatedBuildItem extends EmptyBuildItem {
}
