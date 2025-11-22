package io.quarkiverse.temporal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Function;

import javax.net.ssl.SSLException;

import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import com.uber.m3.tally.StatsReporter;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.quarkiverse.temporal.config.ConnectionRuntimeConfig;
import io.quarkiverse.temporal.config.MTLSRuntimeConfig;
import io.quarkiverse.temporal.config.RpcRetryRuntimeConfig;
import io.quarkiverse.temporal.config.TemporalRuntimeConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.runtime.util.ClassPathUtils;
import io.temporal.common.reporter.MicrometerClientStatsReporter;
import io.temporal.serviceclient.RpcRetryOptions;
import io.temporal.serviceclient.SimpleSslContextBuilder;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

@Recorder
public class WorkflowServiceStubsRecorder {

    final RuntimeValue<TemporalRuntimeConfig> runtimeConfig;

    public WorkflowServiceStubsRecorder(RuntimeValue<TemporalRuntimeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<WorkflowServiceStubs>, WorkflowServiceStubs> createWorkflowServiceStubs(
            boolean micrometerSupported) {
        boolean isMicrometerEnabled = micrometerSupported && runtimeConfig.getValue().metricsEnabled();
        return context -> WorkflowServiceStubs
                .newServiceStubs(createWorkflowServiceStubsOptions(runtimeConfig.getValue().connection(), isMicrometerEnabled));
    }

    public Function<SyntheticCreationalContext<WorkflowServiceStubs>, WorkflowServiceStubs> createQuarkusManagedWorkflowServiceStubs(
            boolean micrometerSupported) {
        boolean isMicrometerEnabled = micrometerSupported && runtimeConfig.getValue().metricsEnabled();
        return context -> WorkflowServiceStubs
                .newServiceStubs(createQuarkusManagedWorkflowServiceStubsOptions(context, runtimeConfig.getValue().connection(),
                        isMicrometerEnabled));
    }

    WorkflowServiceStubsOptions createWorkflowServiceStubsOptions(
            ConnectionRuntimeConfig connection,
            boolean isMicrometerEnabled) {
        if (connection == null) {
            return WorkflowServiceStubsOptions.getDefaultInstance();
        }

        WorkflowServiceStubsOptions.Builder builder = WorkflowServiceStubsOptions.newBuilder()
                .setRpcRetryOptions(createRpcRetryOptions(connection.rpcRetry()))
                .setMetricsScope(createScope(isMicrometerEnabled))
                .setTarget(connection.target())
                .setEnableHttps(connection.enableHttps());

        connection.rpcLongPollTimeout().ifPresent(builder::setRpcLongPollTimeout);

        // API KEY
        if (connection.apiKey().isPresent()) {
            // Create a Metadata object with the Temporal namespace header key.
            String apiKey = connection.apiKey().orElseThrow();
            builder.addApiKey(() -> apiKey);
            try {
                builder.setSslContext(
                        SimpleSslContextBuilder.noKeyOrCertChain().setUseInsecureTrustManager(false).build());
            } catch (SSLException e) {
                throw new ConfigurationException("Failed to create SSL context", e);
            }
        } else {
            // Mutual Transport Layer Security
            MTLSRuntimeConfig mtls = connection.mtls();

            if (mtls.clientCertPath().isPresent() != mtls.clientKeyPath().isPresent()) {
                throw new ConfigurationException("Both client cert and key must be provided");
            }

            if (mtls.clientCertPath().isPresent() && mtls.clientKeyPath().isPresent()) {
                try {
                    SimpleSslContextBuilder sslContextBuilder = SimpleSslContextBuilder.forPKCS8(
                            read(mtls.clientCertPath().get()),
                            read(mtls.clientKeyPath().get()));
                    mtls.password().ifPresent(sslContextBuilder::setKeyPassword);
                    builder.setSslContext(sslContextBuilder.build());
                } catch (SSLException e) {
                    throw new ConfigurationException("Failed to create SSL context", e);
                }
            }
        }

        return builder.build();
    }

    WorkflowServiceStubsOptions createQuarkusManagedWorkflowServiceStubsOptions(
            SyntheticCreationalContext<WorkflowServiceStubs> context,
            ConnectionRuntimeConfig connection,
            boolean isMicrometerEnabled) {
        if (connection == null) {
            return WorkflowServiceStubsOptions.getDefaultInstance();
        }

        WorkflowServiceStubsOptions.Builder builder = WorkflowServiceStubsOptions.newBuilder()
                .setChannel(
                        (ManagedChannel) context.getInjectedReference(Channel.class,
                                GrpcClient.Literal.of("temporal-client")))
                .setMetricsScope(createScope(isMicrometerEnabled))
                .setRpcRetryOptions(createRpcRetryOptions(connection.rpcRetry()));

        connection.rpcLongPollTimeout().ifPresent(builder::setRpcLongPollTimeout);

        MTLSRuntimeConfig mtls = connection.mtls();

        if (mtls.clientCertPath().isPresent() || mtls.clientKeyPath().isPresent()) {
            throw new ConfigurationException(
                    "MTLS must be configured using quarkus.grpc.clients.temporal-client when using Quarkus managed gRPC channel");
        }

        return builder.build();
    }

    RpcRetryOptions createRpcRetryOptions(RpcRetryRuntimeConfig rpcRetry) {
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

    private Scope createScope(boolean isMicrometerEnabled) {
        Duration reportDuration = runtimeConfig.getValue().metricsReportInterval();
        if (isMicrometerEnabled && reportDuration.getSeconds() > 0) {
            MeterRegistry registry = Metrics.globalRegistry;
            StatsReporter reporter = new MicrometerClientStatsReporter(registry);
            // set up a new scope, report every N seconds
            return new RootScopeBuilder()
                    .reporter(reporter)
                    .reportEvery(com.uber.m3.util.Duration.ofSeconds(reportDuration.getSeconds()));
        }
        return null;
    }

    /**
     * Read the content of the path.
     * <p>
     * The file is read from the classpath if it exists, otherwise it is read from
     * the file system.
     *
     * @param path the path, must not be {@code null}
     * @return the content of the file
     */
    static InputStream read(Path path) {
        try {
            final InputStream resource = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(ClassPathUtils.toResourceName(path));
            if (resource != null) {
                return resource;
            } else {
                return Files.newInputStream(path);
            }
        } catch (IOException e) {
            throw new ConfigurationException("Client cert or key file not found", e);
        }
    }
}
