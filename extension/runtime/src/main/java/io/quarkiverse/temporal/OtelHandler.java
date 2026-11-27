package io.quarkiverse.temporal;

import jakarta.enterprise.inject.spi.CDI;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.opentracingshim.OpenTracingShim;
import io.quarkus.core.StartContext;

/**
 * Bridges Temporal's OpenTracing instrumentation to OpenTelemetry.
 * <p>
 * Invoked from a {@code ServiceRegistrar} service registered by the deployment processor.
 */
public final class OtelHandler {

    private OtelHandler() {
    }

    public static void bindOpenTracing(StartContext ctx) {
        // bridge Temporal OpenTracing to OpenTelemetry
        OpenTelemetry openTelemetry = CDI.current().select(OpenTelemetry.class).get();
        if (openTelemetry == null) {
            throw new IllegalStateException("OpenTelemetry not available");
        }

        io.opentracing.Tracer tracer = OpenTracingShim.createTracerShim(openTelemetry);
        io.opentracing.util.GlobalTracer.registerIfAbsent(tracer);
        ctx.onStop(tracer::close);
    }
}
