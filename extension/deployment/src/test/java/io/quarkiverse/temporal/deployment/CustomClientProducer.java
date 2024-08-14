package io.quarkiverse.temporal.deployment;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import jakarta.enterprise.inject.Produces;

public class CustomClientProducer {

    @Produces
    WorkflowClient client() {
        WorkflowServiceStubs workflowServiceStubs = WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.getDefaultInstance());
        return WorkflowClient.newInstance(workflowServiceStubs, WorkflowClientOptions.newBuilder()
                        .setIdentity("Custom Client")
                .build());
    }
}
