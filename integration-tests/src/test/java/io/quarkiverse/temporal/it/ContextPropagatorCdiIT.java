package io.quarkiverse.temporal.it;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.temporal.it.util.MDCContextPropagator;
import io.quarkus.test.junit.QuarkusTest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.context.ContextPropagator;

/**
 * Ensures the ContextPropagator used by WorkflowClient comes from CDI.
 */
@QuarkusTest
public class ContextPropagatorCdiIT {
    @Inject
    WorkflowClient client;

    @Test
    void shouldUseCdiProvidedContextPropagator() {
        WorkflowClientOptions opts = client.getOptions();
        ContextPropagator cp = opts.getContextPropagators().get(0);
        String result = cp instanceof MDCContextPropagator ? "CDI" : (cp == null ? "NULL" : cp.getClass().getName());

        Assertions.assertEquals("CDI", result);
    }
}
