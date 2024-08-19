package io.quarkiverse.temporal;

import io.quarkiverse.temporal.config.ConnectionRuntimeConfig;
import io.quarkiverse.temporal.config.RpcRetryRuntimeConfig;
import io.quarkiverse.temporal.config.TemporalRuntimeConfig;
import io.quarkus.runtime.annotations.Recorder;
import io.temporal.serviceclient.RpcRetryOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

@Recorder
public class WorkflowServiceStubsRecorder {

    public WorkflowServiceStubsRecorder(TemporalRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    final TemporalRuntimeConfig runtimeConfig;

    public RpcRetryOptions createRpcRetryOptions(RpcRetryRuntimeConfig rpcRetry) {
        if (rpcRetry == null) {
            return RpcRetryOptions.getDefaultInstance();
        }

        RpcRetryOptions.Builder builder = RpcRetryOptions.newBuilder()
                .setInitialInterval(rpcRetry.initialInterval())
                .setCongestionInitialInterval(rpcRetry.congestionInitialInterval())
                .setExpiration(rpcRetry.expiration())
                .setBackoffCoefficient(rpcRetry.backoffCoefficient())
                .setMaximumAttempts(rpcRetry.maximumAttempts())
                .setMaximumJitterCoefficient(rpcRetry.maximumJitterCoefficient());

        rpcRetry.maximumInterval().ifPresent(builder::setMaximumInterval);
        rpcRetry.doNotRetry().ifPresent(codes -> codes.forEach(code -> builder.addDoNotRetry(code, null)));
        return builder.build();
    }

    public WorkflowServiceStubsOptions createWorkflowServiceStubsOptions(ConnectionRuntimeConfig connection) {
        if (connection == null) {
            return WorkflowServiceStubsOptions.getDefaultInstance();
        }
        WorkflowServiceStubsOptions.Builder builder = WorkflowServiceStubsOptions.newBuilder()
                .setRpcRetryOptions(createRpcRetryOptions(connection.rpcRetry()))
                .setTarget(connection.target())
                .setEnableHttps(connection.enableHttps());
        return builder.build();
    }

    public WorkflowServiceStubs createWorkflowServiceStubs() {
        return WorkflowServiceStubs.newServiceStubs(createWorkflowServiceStubsOptions(runtimeConfig.connection()));
    }

}
