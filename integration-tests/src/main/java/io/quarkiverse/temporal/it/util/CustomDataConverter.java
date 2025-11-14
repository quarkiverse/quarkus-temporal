package io.quarkiverse.temporal.it.util;

import java.lang.reflect.Type;
import java.util.Optional;

import io.temporal.api.common.v1.Payload;
import io.temporal.api.common.v1.Payloads;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.DataConverterException;
import io.temporal.common.converter.DefaultDataConverter;

/**
 * A custom {@link DataConverter} bean to be picked up from CDI.
 * We wrap Temporal's default to make the instance unique and detectable.
 */
public class CustomDataConverter implements DataConverter {
    static final DataConverter INSTANCE = DefaultDataConverter.newDefaultInstance();

    @Override
    public <T> Optional<Payload> toPayload(T value) throws DataConverterException {
        return INSTANCE.toPayload(value);
    }

    @Override
    public <T> T fromPayload(Payload payload, Class<T> valueClass, Type valueType) throws DataConverterException {
        return INSTANCE.fromPayload(payload, valueClass, valueType);
    }

    @Override
    public Optional<Payloads> toPayloads(Object... values) throws DataConverterException {
        return INSTANCE.toPayloads(values);
    }

    @Override
    public <T> T fromPayloads(int index, Optional<Payloads> content, Class<T> valueClass, Type valueType)
            throws DataConverterException {
        return INSTANCE.fromPayloads(index, content, valueClass, valueType);
    }
}
