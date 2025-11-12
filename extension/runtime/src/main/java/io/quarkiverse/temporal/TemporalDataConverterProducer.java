package io.quarkiverse.temporal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

import io.temporal.common.converter.DataConverter;

@ApplicationScoped
public class TemporalDataConverterProducer {

    private final Instance<DataConverter> userProvided;

    public TemporalDataConverterProducer(Instance<DataConverter> userProvided) {
        this.userProvided = userProvided;
    }

    @Produces
    @ApplicationScoped
    public DataConverter dataConverter() {
        return userProvided.isResolvable()
                ? userProvided.get()
                : null;
    }
}
