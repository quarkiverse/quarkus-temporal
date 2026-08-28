package io.quarkiverse.temporal.order.model.workflow;

import java.time.Duration;

public record OrderEntityConfig(Duration orderExpiryDuration, Duration maxPollingAwaitTime) {
}
