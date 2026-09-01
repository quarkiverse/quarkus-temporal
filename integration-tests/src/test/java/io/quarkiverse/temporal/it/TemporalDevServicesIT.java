package io.quarkiverse.temporal.it;

import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.temporal.api.workflowservice.v1.DescribeNamespaceRequest;
import io.temporal.client.WorkflowClient;

@QuarkusTest
@TestProfile(TemporalDevServicesIT.DevServicesProfile.class)
public class TemporalDevServicesIT {

    private static final String NAMESPACE = "dev-services-test";

    @Inject
    WorkflowClient workflowClient;

    @Test
    void startsTemporalAndProvisionsConfiguredNamespace() {
        Assertions.assertDoesNotThrow(() -> workflowClient.getWorkflowServiceStubs().blockingStub()
                .describeNamespace(DescribeNamespaceRequest.newBuilder().setNamespace(NAMESPACE).build()));
    }

    public static class DevServicesProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.temporal.enable-mock", "false",
                    "quarkus.temporal.start-workers", "false",
                    "quarkus.temporal.devservices.namespaces", NAMESPACE);
        }
    }
}
