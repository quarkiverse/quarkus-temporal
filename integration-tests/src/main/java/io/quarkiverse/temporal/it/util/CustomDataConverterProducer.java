package io.quarkiverse.temporal.it.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.temporal.common.converter.DataConverter;

/**
 * Produces a custom {@link DataConverter} bean to be picked up from CDI.
 */
@ApplicationScoped
public class CustomDataConverterProducer {

    @Produces
    public DataConverter produceDataConverter() {
        return new CustomDataConverter();
    }
}
