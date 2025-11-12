package io.quarkiverse.temporal.it.dataconverter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.quarkus.arc.Unremovable;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.DefaultDataConverter;
import io.temporal.common.converter.JacksonJsonPayloadConverter;

/**
 * Produces a custom DataConverter bean to be picked up from CDI.
 * We wrap the default with a custom Jackson converter to make the instance unique and detectable.
 */
@ApplicationScoped
public class CustomMarkerDataConverter {

    static final DataConverter INSTANCE = DefaultDataConverter.newDefaultInstance()
            .withPayloadConverterOverrides(new JacksonJsonPayloadConverter());

    @Produces
    @ApplicationScoped
    @Unremovable
    DataConverter dataConverter() {
        return INSTANCE;
    }
}