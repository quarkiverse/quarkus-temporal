package io.quarkiverse.temporal;

import io.quarkiverse.temporal.config.ConnectionRuntimeConfig;
import io.quarkiverse.temporal.config.TemporalRuntimeConfig;
import io.quarkus.runtime.annotations.Recorder;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

@Recorder
public class WorkflowServiceStubsRecorder {

    public WorkflowServiceStubsRecorder(TemporalRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    final TemporalRuntimeConfig runtimeConfig;

    public WorkflowServiceStubsOptions createWorkflowServiceStubsOptions() {
        if (runtimeConfig == null) {
            return WorkflowServiceStubsOptions.getDefaultInstance();
        }
        ConnectionRuntimeConfig connection = runtimeConfig.connection();
        WorkflowServiceStubsOptions.Builder builder = WorkflowServiceStubsOptions.newBuilder()
                .setTarget(connection.target())
                .setEnableHttps(connection.enableHttps());
        return builder.build();
    }

    public WorkflowServiceStubs createWorkflowServiceStubs() {
        return WorkflowServiceStubs.newServiceStubs(createWorkflowServiceStubsOptions());
    }
}
