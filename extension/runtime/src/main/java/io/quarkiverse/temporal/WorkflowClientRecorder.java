package io.quarkiverse.temporal;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.spi.CDI;

import io.quarkiverse.temporal.config.TemporalBuildtimeConfig;
import io.quarkiverse.temporal.config.TemporalRuntimeConfig;
import io.quarkus.runtime.annotations.Recorder;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.context.ContextPropagator;
import io.temporal.common.interceptors.WorkflowClientInterceptor;
import io.temporal.opentracing.OpenTracingClientInterceptor;
import io.temporal.serviceclient.WorkflowServiceStubs;

/**
 * Recorder class responsible for creating and configuring instances of {@link WorkflowClient} and
 * {@link WorkflowClientOptions} for use with Temporal workflows. This class is used in the context
 * of Quarkus's build and runtime steps to set up the necessary Temporal client components.
 */
@Recorder
public class WorkflowClientRecorder {

    /**
     * The runtime configuration for Temporal.
     */
    final TemporalRuntimeConfig runtimeConfig;

    /**
     * The build-time configuration for Temporal.
     */
    final TemporalBuildtimeConfig buildtimeConfig;

    /**
     * Constructs a new instance of {@code WorkflowClientRecorder} with the given runtime and build-time configuration.
     *
     * @param runtimeConfig The runtime configuration for Temporal.
     * @param buildtimeConfig The build-time configuration for Temporal.
     */
    public WorkflowClientRecorder(TemporalRuntimeConfig runtimeConfig, TemporalBuildtimeConfig buildtimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.buildtimeConfig = buildtimeConfig;
    }

    /**
     * Creates an instance of {@link WorkflowClientOptions} based on the provided propagators and telemetry settings.
     *
     * @param openTelemetryEnabled A flag indicating whether OpenTelemetry is enabled.
     * @return A configured {@link WorkflowClientOptions} instance.
     */
    public WorkflowClientOptions createWorkflowClientOptions(boolean openTelemetryEnabled) {
        if (runtimeConfig == null) {
            return WorkflowClientOptions.getDefaultInstance();
        }
        WorkflowClientOptions.Builder builder = WorkflowClientOptions.newBuilder()
                .setNamespace(runtimeConfig.namespace());

        runtimeConfig.identity().ifPresent(builder::setIdentity);

        // discover interceptors
        List<WorkflowClientInterceptor> interceptors = CDI.current().select(WorkflowClientInterceptor.class).stream()
                .collect(Collectors.toCollection(ArrayList::new));
        if (openTelemetryEnabled) {
            interceptors.add(new OpenTracingClientInterceptor());
        }
        if (!interceptors.isEmpty()) {
            builder.setInterceptors(interceptors.toArray(new WorkflowClientInterceptor[0]));
        }

        // discover propagators
        List<ContextPropagator> propagators = CDI.current().select(ContextPropagator.class).stream()
                .collect(Collectors.toCollection(ArrayList::new));
        if (!propagators.isEmpty()) {
            builder.setContextPropagators(propagators);
        }

        return builder.validateAndBuildWithDefaults();
    }

    /**
     * Creates a new instance of {@link WorkflowClient} using the provided {@link WorkflowServiceStubs},
     * context propagators, and telemetry settings.
     *
     * @param serviceStubs The {@link WorkflowServiceStubs} used to connect to the Temporal service.
     * @param openTelemetryEnabled A flag indicating whether OpenTelemetry is enabled.
     * @return A configured {@link WorkflowClient} instance.
     */
    public WorkflowClient createWorkflowClient(WorkflowServiceStubs serviceStubs, boolean openTelemetryEnabled) {
        return WorkflowClient.newInstance(serviceStubs, createWorkflowClientOptions(openTelemetryEnabled));
    }

}