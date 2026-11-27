package io.quarkiverse.temporal;

import io.quarkiverse.temporal.config.TemporalRuntimeConfig;
import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;

/**
 * Creates the {@link WorkflowClient} synthetic bean from the injected {@link WorkflowServiceStubs} and the CDI-provided
 * interceptors, context propagators and data converters.
 */
public class WorkflowClientHandler implements BeanCreator<WorkflowClient> {

    @Override
    public WorkflowClient create(SyntheticCreationalContext<WorkflowClient> context) {
        return WorkflowClient.newInstance(context.getInjectedReference(WorkflowServiceStubs.class),
                createWorkflowClientOptions(context, TemporalConfigs.runtime()));
    }

    /**
     * Creates an instance of {@link WorkflowClientOptions} based on the runtime configuration and the CDI wiring.
     *
     * @param context the synthetic creational context
     * @param runtimeConfig the Temporal runtime configuration
     * @return A configured {@link WorkflowClientOptions} instance.
     */
    public static WorkflowClientOptions createWorkflowClientOptions(SyntheticCreationalContext<WorkflowClient> context,
            TemporalRuntimeConfig runtimeConfig) {
        if (runtimeConfig == null) {
            return WorkflowClientOptions.getDefaultInstance();
        }

        return WorkflowClientOptionsSupport.buildFromContext(
                context,
                runtimeConfig.namespace(),
                runtimeConfig.identity());
    }
}
