package io.quarkiverse.temporal;

import jakarta.enterprise.inject.spi.CDI;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.opentracingshim.OpenTracingShim;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class OtelRecorder {

    public void bindOpenTracing(ShutdownContext shutdownContext) {
        // bridge Temporal OpenTracing to OpenTelemetry
        OpenTelemetry openTelemetry = CDI.current().select(OpenTelemetry.class).get();
        if (openTelemetry == null) {
            throw new IllegalStateException("OpenTelemetry not available");
        }

        io.opentracing.Tracer tracer = OpenTracingShim.createTracerShim(openTelemetry);
        io.opentracing.util.GlobalTracer.registerIfAbsent(tracer);
        shutdownContext.addShutdownTask(tracer::close);

    }
}
