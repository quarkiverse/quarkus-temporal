package io.quarkiverse.temporal.it.util;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.MDC;

import com.google.protobuf.ByteString;

import io.temporal.api.common.v1.Payload;
import io.temporal.common.context.ContextPropagator;
import io.temporal.common.converter.GlobalDataConverter;

/**
 * A {@link ContextPropagator} implementation that propagates the SLF4J MDC
 * (Mapped Diagnostic Context) across Temporal workflow and activity boundaries.
 * This class ensures that MDC entries with keys starting with "X-" are
 * propagated.
 */
public class MDCContextPropagator implements ContextPropagator {

    public MDCContextPropagator() {
        super();
    }

    /**
     * Gets the name of the context propagator.
     *
     * @return the name of the context propagator, which is the fully qualified
     *         class name.
     */
    @Override
    public String getName() {
        return this.getClass().getName();
    }

    /**
     * Retrieves the current MDC context to be propagated.
     *
     * @return a map containing the current MDC context, filtered to include only
     *         entries with keys starting with "X-".
     */
    @Override
    public Object getCurrentContext() {
        Map<String, String> context = new HashMap<>();
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        if (mdcContext != null) {
            mdcContext.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("X-"))
                    .forEach(entry -> context.put(entry.getKey(), entry.getValue()));
        }
        return context;
    }

    /**
     * Sets the current MDC context from the given context map.
     *
     * @param context the context map containing MDC entries to be set.
     */
    @Override
    public void setCurrentContext(Object context) {
        if (context instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> contextMap = (Map<String, String>) context;
            contextMap.forEach(MDC::put);
        }
    }

    /**
     * Serializes the given context map to a map of Payloads.
     *
     * @param context the context map containing MDC entries to be serialized.
     * @return a map of Payloads representing the serialized context.
     */
    @Override
    public Map<String, Payload> serializeContext(Object context) {
        if (!(context instanceof Map)) {
            return new HashMap<>();
        }
        @SuppressWarnings("unchecked")
        Map<String, String> contextMap = (Map<String, String>) context;
        Map<String, Payload> serializedContext = new HashMap<>();
        contextMap.forEach((key, value) -> GlobalDataConverter.get().toPayload(value)
                .ifPresent(payload -> serializedContext.put(key, payload)));
        return serializedContext;
    }

    /**
     * Deserializes the given map of Payloads to a context map.
     *
     * @param context the map of Payloads to be deserialized.
     * @return a context map containing the deserialized MDC entries.
     */
    @Override
    public Object deserializeContext(Map<String, Payload> context) {
        Map<String, String> contextMap = new HashMap<>();
        context.forEach((key, payload) -> {

            // Handle empty {} when the data value is empty
            // Adding opentracing seems to add a new value with empty data
            // and the dataconverter throws an error
            //
            // {_tracer-data=metadata {
            //    key: "encoding"
            //    value: "json/plain"
            //    }
            // data: "{}"
            // }
            String payloadValue = ""; // default value

            // Convert data to string to compare
            ByteString data = payload.getData();

            // Check the value to see if it "empty"
            if (data != null && !data.isEmpty()) {

                // Convert to string
                String theData = data.toStringUtf8();

                // Check if the value isn't {}'s
                if (!theData.equals("{}")) {
                    payloadValue = GlobalDataConverter.get().fromPayload(payload, String.class, String.class);
                }
            }

            // Add the value into the map
            contextMap.put(key, payloadValue);
        });
        return contextMap;
    }
}
