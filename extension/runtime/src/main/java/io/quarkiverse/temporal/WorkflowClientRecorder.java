package io.quarkiverse.temporal;

import java.util.function.Function;

import io.quarkiverse.temporal.config.TemporalBuildtimeConfig;
import io.quarkiverse.temporal.config.TemporalRuntimeConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
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
     * @param context the workflow Synthetic Creation Context
     * @return A configured {@link WorkflowClientOptions} instance.
     */
    public WorkflowClientOptions createWorkflowClientOptions(
            SyntheticCreationalContext<WorkflowClient> context) {

        if (runtimeConfig == null) {
            return WorkflowClientOptions.getDefaultInstance();
        }

        return WorkflowClientOptionsSupport.buildFromContext(
                context,
                runtimeConfig.namespace(),
                runtimeConfig.identity());
    }

    /**
     * Creates a new instance of {@link WorkflowClient} using the provided {@link WorkflowServiceStubs},
     * context propagators, and telemetry settings.
     *
     * @return A configured {@link WorkflowClient} instance.
     */
    public Function<SyntheticCreationalContext<WorkflowClient>, WorkflowClient> createWorkflowClient() {
        return context -> WorkflowClient.newInstance(context.getInjectedReference(WorkflowServiceStubs.class),
                createWorkflowClientOptions(context));
    }

}
