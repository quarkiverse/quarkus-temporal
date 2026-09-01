package io.quarkiverse.temporal.it.freshness;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.temporal.client.WorkflowClient;

/**
 * Stands in for ordinary application code (a REST resource, a service) that injects
 * {@link WorkflowClient} once and is never reconstructed per test - unlike the test class
 * itself, which JUnit reconstructs for every test method.
 */
@ApplicationScoped
public class FreshnessClientHolder {

    @Inject
    WorkflowClient workflowClient;

    public WorkflowClient get() {
        return workflowClient;
    }
}
