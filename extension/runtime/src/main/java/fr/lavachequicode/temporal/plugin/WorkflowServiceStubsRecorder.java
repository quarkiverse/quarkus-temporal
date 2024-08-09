package fr.lavachequicode.temporal.plugin;

import fr.lavachequicode.temporal.plugin.config.ConnectionRuntimeConfig;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

@Recorder
public class WorkflowServiceStubsRecorder {

    public RuntimeValue<WorkflowServiceStubsOptions> createWorkflowServiceStubsOptions(ConnectionRuntimeConfig runtimeConfig) {
        WorkflowServiceStubsOptions.Builder builder = WorkflowServiceStubsOptions.newBuilder();
        runtimeConfig.target().ifPresent(builder::setTarget);
        return new RuntimeValue<>(builder.build());
    }

    public WorkflowServiceStubs createWorkflowServiceStubs(RuntimeValue<WorkflowServiceStubsOptions> options) {
        return WorkflowServiceStubs.newServiceStubs(options.getValue());
    }
}
