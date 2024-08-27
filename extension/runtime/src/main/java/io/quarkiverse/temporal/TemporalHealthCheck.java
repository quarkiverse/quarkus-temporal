package io.quarkiverse.temporal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import io.temporal.client.WorkflowClient;

/**
 * A readiness health check class for monitoring the health of Temporal service.
 * This class implements the HealthCheck interface to provide a custom health check
 * for Temporal's workflow service.
 */
@Readiness
@ApplicationScoped
public class TemporalHealthCheck implements HealthCheck {

    // Injecting the WorkflowClient to interact with Temporal services
    @Inject
    WorkflowClient workflowClient;

    /**
     * The health check logic to determine if the Temporal service is healthy and ready to handle requests.
     *
     * @return HealthCheckResponse object indicating the health status of the Temporal service.
     */
    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder responseBuilder = HealthCheckResponse.named("Temporal");

        // Get the target HTTP URL information from WorkflowClient
        String target = workflowClient.getWorkflowServiceStubs().getOptions().getTarget();

        try {
            // Perform the health check by calling Temporal's health check API
            io.grpc.health.v1.HealthCheckResponse response = workflowClient.getWorkflowServiceStubs().healthCheck();

            // Retrieve the status from the health check response
            io.grpc.health.v1.HealthCheckResponse.ServingStatus status = response.getStatus();

            // Update the HealthCheckResponseBuilder based on the status of the service
            responseBuilder = status == io.grpc.health.v1.HealthCheckResponse.ServingStatus.SERVING
                    ? responseBuilder.up().withData(target, "UP") // If the service is serving, mark as up
                    : responseBuilder.down().withData(target, "DOWN"); // If the service is not serving, mark as down
        } catch (Exception e) {
            responseBuilder.down().withData(target, "DOWN");
        }

        return responseBuilder.build();
    }
}