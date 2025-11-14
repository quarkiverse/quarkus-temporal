package io.quarkiverse.temporal.it;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.temporal.it.util.CustomDataConverter;
import io.quarkus.test.junit.QuarkusTest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.converter.DataConverter;

/**
 * Ensures the DataConverter used by WorkflowClient comes from CDI.
 */
@QuarkusTest
public class DataConverterCdiIT {
    @Inject
    WorkflowClient client;

    @Test
    void shouldUseCdiProvidedDataConverter() {
        WorkflowClientOptions opts = client.getOptions();
        DataConverter dc = opts.getDataConverter();
        String result = dc instanceof CustomDataConverter ? "CDI" : (dc == null ? "NULL" : dc.getClass().getName());

        Assertions.assertEquals("CDI", result);
    }
}
