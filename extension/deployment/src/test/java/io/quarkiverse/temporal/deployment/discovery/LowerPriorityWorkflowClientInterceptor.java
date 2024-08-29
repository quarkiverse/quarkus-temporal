package io.quarkiverse.temporal.deployment.discovery;

import java.util.Optional;

import jakarta.annotation.Priority;
import jakarta.inject.Singleton;

import io.quarkus.arc.Unremovable;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.ActivityCompletionClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.interceptors.WorkflowClientCallsInterceptor;
import io.temporal.common.interceptors.WorkflowClientInterceptor;

@Singleton
@Unremovable
@Priority(1)
public class LowerPriorityWorkflowClientInterceptor implements WorkflowClientInterceptor {
    @Override
    public WorkflowStub newUntypedWorkflowStub(String workflowType, WorkflowOptions options, WorkflowStub next) {
        return next;
    }

    @Override
    public WorkflowStub newUntypedWorkflowStub(WorkflowExecution execution, Optional<String> workflowType, WorkflowStub next) {
        return next;
    }

    @Override
    public ActivityCompletionClient newActivityCompletionClient(ActivityCompletionClient next) {
        return next;
    }

    @Override
    public WorkflowClientCallsInterceptor workflowClientCallsInterceptor(WorkflowClientCallsInterceptor next) {
        return next;
    }
}