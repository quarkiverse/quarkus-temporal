package io.quarkiverse.temporal;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import io.quarkiverse.temporal.config.TemporalBuildtimeConfig;
import io.quarkiverse.temporal.config.TemporalRuntimeConfig;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.context.ContextPropagator;
import io.temporal.opentracing.OpenTracingClientInterceptor;
import io.temporal.serviceclient.WorkflowServiceStubs;

@Recorder
public class WorkflowClientRecorder {

    public WorkflowClientRecorder(TemporalRuntimeConfig runtimeConfig, TemporalBuildtimeConfig buildtimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.buildtimeConfig = buildtimeConfig;
    }

    final TemporalRuntimeConfig runtimeConfig;
    final TemporalBuildtimeConfig buildtimeConfig;

    public WorkflowClientOptions createWorkflowClientOptions(List<Class<? extends ContextPropagator>> propagatorsClasses,
            boolean openTelemetryEnabled) {
        if (runtimeConfig == null) {
            return WorkflowClientOptions.getDefaultInstance();
        }
        WorkflowClientOptions.Builder builder = WorkflowClientOptions.newBuilder()
                .setNamespace(runtimeConfig.namespace());

        runtimeConfig.identity().ifPresent(builder::setIdentity);

        if (openTelemetryEnabled) {
            builder.setInterceptors(new OpenTracingClientInterceptor());
        }

        if (propagatorsClasses != null && !propagatorsClasses.isEmpty()) {

            List<ContextPropagator> propagators = new ArrayList<>();
            for (Class<? extends ContextPropagator> propagatorClass : propagatorsClasses) {
                try {
                    propagators.add(propagatorClass.getDeclaredConstructor().newInstance());
                } catch (NoSuchMethodException | InstantiationException | IllegalAccessException
                        | InvocationTargetException e) {
                    throw new ConfigurationException(
                            "Context propagator " + propagatorClass + " is missing a default constructor", e);
                }
            }
            builder.setContextPropagators(propagators);
        }

        return builder.build();
    }

    public WorkflowClient createWorkflowClient(WorkflowServiceStubs serviceStubs,
            List<Class<? extends ContextPropagator>> propagators, boolean openTelemetryEnabled) {
        return WorkflowClient.newInstance(serviceStubs, createWorkflowClientOptions(propagators, openTelemetryEnabled));
    }

}