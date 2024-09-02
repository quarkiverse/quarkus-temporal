package io.quarkiverse.temporal;

import java.time.Duration;
import java.util.function.Function;

import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import com.uber.m3.tally.StatsReporter;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.quarkiverse.temporal.config.ConnectionRuntimeConfig;
import io.quarkiverse.temporal.config.RpcRetryRuntimeConfig;
import io.quarkiverse.temporal.config.TemporalRuntimeConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.runtime.annotations.Recorder;
import io.temporal.common.reporter.MicrometerClientStatsReporter;
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

    public WorkflowServiceStubsOptions createWorkflowServiceStubsOptions(
            ConnectionRuntimeConfig connection,
            boolean isMicrometerEnabled) {
        if (connection == null) {
            return WorkflowServiceStubsOptions.getDefaultInstance();
        }

        Duration reportDuration = runtimeConfig.metricsReportInterval();
        Scope scope = null;
        if (isMicrometerEnabled && reportDuration.getSeconds() > 0) {
            MeterRegistry registry = Metrics.globalRegistry;
            StatsReporter reporter = new MicrometerClientStatsReporter(registry);
            // set up a new scope, report every N seconds
            scope = new RootScopeBuilder()
                    .reporter(reporter)
                    .reportEvery(com.uber.m3.util.Duration.ofSeconds(reportDuration.getSeconds()));
        }

        WorkflowServiceStubsOptions.Builder builder = WorkflowServiceStubsOptions.newBuilder()
                .setRpcRetryOptions(createRpcRetryOptions(connection.rpcRetry()))
                .setTarget(connection.target())
                .setMetricsScope(scope)
                .setEnableHttps(connection.enableHttps());
        return builder.build();
    }

    public Function<SyntheticCreationalContext<WorkflowServiceStubs>, WorkflowServiceStubs> createWorkflowServiceStubs(
            boolean micrometerSupported) {
        boolean isMicrometerEnabled = micrometerSupported && runtimeConfig.metricsEnabled();
        return context -> WorkflowServiceStubs
                .newServiceStubs(createWorkflowServiceStubsOptions(runtimeConfig.connection(), isMicrometerEnabled));
    }

    public WorkflowServiceStubsOptions createQuarkusManagedWorkflowServiceStubsOptions(
            SyntheticCreationalContext<WorkflowServiceStubs> context,
            ConnectionRuntimeConfig connection,
            boolean isMicrometerEnabled) {
        if (connection == null) {
            return WorkflowServiceStubsOptions.getDefaultInstance();
        }

        Duration reportDuration = runtimeConfig.metricsReportInterval();
        Scope scope = null;
        if (isMicrometerEnabled && reportDuration.getSeconds() > 0) {
            MeterRegistry registry = Metrics.globalRegistry;
            StatsReporter reporter = new MicrometerClientStatsReporter(registry);
            // set up a new scope, report every N seconds
            scope = new RootScopeBuilder()
                    .reporter(reporter)
                    .reportEvery(com.uber.m3.util.Duration.ofSeconds(reportDuration.getSeconds()));
        }

        WorkflowServiceStubsOptions.Builder builder = WorkflowServiceStubsOptions.newBuilder()
                .setChannel(
                        (ManagedChannel) context.getInjectedReference(Channel.class, GrpcClient.Literal.of("temporal-client")))
                .setMetricsScope(scope)
                .setRpcRetryOptions(createRpcRetryOptions(connection.rpcRetry()));
        return builder.build();
    }

    public Function<SyntheticCreationalContext<WorkflowServiceStubs>, WorkflowServiceStubs> createQuarkusManagedWorkflowServiceStubs(
            boolean micrometerSupported) {
        boolean isMicrometerEnabled = micrometerSupported && runtimeConfig.metricsEnabled();
        return context -> WorkflowServiceStubs
                .newServiceStubs(createQuarkusManagedWorkflowServiceStubsOptions(context, runtimeConfig.connection(),
                        isMicrometerEnabled));
    }
}
