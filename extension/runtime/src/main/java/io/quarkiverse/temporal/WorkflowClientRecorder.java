package io.quarkiverse.temporal;

import io.quarkiverse.temporal.config.TemporalRuntimeConfig;
import io.quarkus.runtime.annotations.Recorder;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;

@Recorder
public class WorkflowClientRecorder {

    public WorkflowClientRecorder(TemporalRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    final TemporalRuntimeConfig runtimeConfig;

    public WorkflowClientOptions createWorkflowClientOptions() {
        if (runtimeConfig == null) {
            return WorkflowClientOptions.getDefaultInstance();
        }
        WorkflowClientOptions.Builder builder = WorkflowClientOptions.newBuilder()
                .setNamespace(runtimeConfig.namespace());

        runtimeConfig.identity().ifPresent(builder::setIdentity);

        return builder.build();
    }

    public WorkflowClient createWorkflowClient(WorkflowServiceStubs serviceStubs) {
        return WorkflowClient.newInstance(serviceStubs, createWorkflowClientOptions());
    }
}
