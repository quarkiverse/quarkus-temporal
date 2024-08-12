package io.quarkiverse.temporal;

import io.quarkiverse.temporal.config.ConnectionRuntimeConfig;
import io.quarkus.runtime.annotations.Recorder;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

@Recorder
public class WorkflowServiceStubsRecorder {

    public WorkflowServiceStubsRecorder(ConnectionRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    final ConnectionRuntimeConfig runtimeConfig;

    public WorkflowServiceStubsOptions createWorkflowServiceStubsOptions() {
        if (runtimeConfig == null) {
            return WorkflowServiceStubsOptions.getDefaultInstance();
        }
        WorkflowServiceStubsOptions.Builder builder = WorkflowServiceStubsOptions.newBuilder()
                .setTarget(runtimeConfig.target())
                .setEnableHttps(runtimeConfig.enableHttps());
        return builder.build();
    }

    public WorkflowServiceStubs createWorkflowServiceStubs() {
        return WorkflowServiceStubs.newServiceStubs(createWorkflowServiceStubsOptions());
    }
}
