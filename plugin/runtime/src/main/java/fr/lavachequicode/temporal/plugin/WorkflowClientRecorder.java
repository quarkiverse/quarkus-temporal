package fr.lavachequicode.temporal.plugin;

import io.quarkus.runtime.annotations.Recorder;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;

@Recorder
public class WorkflowClientRecorder {

    public WorkflowClient createWorkflowClient(WorkflowServiceStubs serviceStubs) {
        return WorkflowClient.newInstance(serviceStubs);
    }
}
