package io.quarkiverse.temporal;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import io.temporal.api.workflowservice.v1.GetSystemInfoRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;

@ApplicationScoped
@Readiness
public class TemporalHealthCheck implements HealthCheck {
    private final WorkflowClient workflowClient;

    public TemporalHealthCheck(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder responseBuilder = HealthCheckResponse.named("Temporal");
        String target = "Unknown";

        try {
            WorkflowServiceStubs workflowServiceStubs = workflowClient.getWorkflowServiceStubs();
            target = workflowServiceStubs.getOptions().getTarget();
            workflowServiceStubs
                    .blockingStub()
                    .getSystemInfo(GetSystemInfoRequest.newBuilder().build());

            return responseBuilder.up().withData(target, "UP").build();

        } catch (Exception e) {
            return responseBuilder
                    .down()
                    .withData(target, "DOWN")
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
