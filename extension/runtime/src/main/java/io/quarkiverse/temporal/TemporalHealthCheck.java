package io.quarkiverse.temporal;

import io.temporal.api.workflowservice.v1.GetSystemInfoRequest;
import io.temporal.api.workflowservice.v1.GetSystemInfoResponse;
import io.temporal.serviceclient.WorkflowServiceStubs;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

@ApplicationScoped
@Readiness
public class TemporalHealthCheck implements HealthCheck {
    WorkflowServiceStubs workflowServiceStubs;

    public TemporalHealthCheck(WorkflowServiceStubs workflowServiceStubs) {
        this.workflowServiceStubs = workflowServiceStubs;
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder responseBuilder = HealthCheckResponse.named("Temporal");
        String target = "Unknown";

        try {
            if (workflowServiceStubs != null) {
                target = workflowServiceStubs.getOptions().getTarget();
                GetSystemInfoResponse systemInfo = workflowServiceStubs
                        .blockingStub()
                        .getSystemInfo(GetSystemInfoRequest.newBuilder().build());

                return responseBuilder.up().withData(target, "UP").build();
            }
            return responseBuilder
                    .down()
                    .withData("error", "Stubs not initialized")
                    .build();
        } catch (Exception e) {
            return responseBuilder
                    .down()
                    .withData(target, "DOWN")
                    .withData("error", e.getMessage())
                    .build();
        }
    }
}
