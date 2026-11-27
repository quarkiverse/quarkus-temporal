package io.quarkiverse.temporal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import javax.net.ssl.SSLException;

import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import com.uber.m3.tally.StatsReporter;

import io.grpc.Channel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.quarkiverse.temporal.config.ConnectionRuntimeConfig;
import io.quarkiverse.temporal.config.MTLSRuntimeConfig;
import io.quarkiverse.temporal.config.RpcRetryRuntimeConfig;
import io.quarkiverse.temporal.config.TemporalRuntimeConfig;
import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.runtime.util.ClassPathUtils;
import io.temporal.common.reporter.MicrometerClientStatsReporter;
import io.temporal.serviceclient.RpcRetryOptions;
import io.temporal.serviceclient.SimpleSslContextBuilder;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

/**
 * Creates the {@link WorkflowServiceStubs} synthetic bean, either with a built-in gRPC channel or with the Quarkus
 * managed {@code temporal-client} gRPC channel.
 * <p>
 * Bean parameters:
 * <ul>
 * <li>{@value #PARAM_QUARKUS_MANAGED}: whether the Quarkus managed gRPC channel is used</li>
 * <li>{@value #PARAM_MICROMETER_SUPPORTED}: whether Micrometer metrics are supported by the application</li>
 * </ul>
 */
public class WorkflowServiceStubsHandler implements BeanCreator<WorkflowServiceStubs> {

    public static final String PARAM_QUARKUS_MANAGED = "quarkusManaged";
    public static final String PARAM_MICROMETER_SUPPORTED = "micrometerSupported";

    @Override
    public WorkflowServiceStubs create(SyntheticCreationalContext<WorkflowServiceStubs> context) {
        TemporalRuntimeConfig runtimeConfig = TemporalConfigs.runtime();
        boolean quarkusManaged = Boolean.TRUE.equals(context.getParams().get(PARAM_QUARKUS_MANAGED));
        boolean micrometerSupported = Boolean.TRUE.equals(context.getParams().get(PARAM_MICROMETER_SUPPORTED));
        boolean isMicrometerEnabled = micrometerSupported && runtimeConfig.metricsEnabled();

        if (quarkusManaged) {
            return WorkflowServiceStubs.newServiceStubs(
                    createQuarkusManagedWorkflowServiceStubsOptions(context, runtimeConfig, isMicrometerEnabled));
        }
        return WorkflowServiceStubs.newServiceStubs(createWorkflowServiceStubsOptions(runtimeConfig, isMicrometerEnabled));
    }

    static WorkflowServiceStubsOptions createWorkflowServiceStubsOptions(
            TemporalRuntimeConfig runtimeConfig,
            boolean isMicrometerEnabled) {
        ConnectionRuntimeConfig connection = runtimeConfig.connection();
        if (connection == null) {
            return WorkflowServiceStubsOptions.getDefaultInstance();
        }

        WorkflowServiceStubsOptions.Builder builder = WorkflowServiceStubsOptions.newBuilder()
                .setRpcRetryOptions(createRpcRetryOptions(connection.rpcRetry()))
                .setMetricsScope(createScope(runtimeConfig, isMicrometerEnabled))
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

    static WorkflowServiceStubsOptions createQuarkusManagedWorkflowServiceStubsOptions(
            SyntheticCreationalContext<WorkflowServiceStubs> context,
            TemporalRuntimeConfig runtimeConfig,
            boolean isMicrometerEnabled) {
        ConnectionRuntimeConfig connection = runtimeConfig.connection();
        if (connection == null) {
            return WorkflowServiceStubsOptions.getDefaultInstance();
        }

        WorkflowServiceStubsOptions.Builder builder = WorkflowServiceStubsOptions.newBuilder()
                .setChannel(
                        QuarkusManagedChannel.of(context.getInjectedReference(Channel.class,
                                GrpcClient.Literal.of("temporal-client"))))
                .setMetricsScope(createScope(runtimeConfig, isMicrometerEnabled))
                .setRpcRetryOptions(createRpcRetryOptions(connection.rpcRetry()));

        connection.rpcLongPollTimeout().ifPresent(builder::setRpcLongPollTimeout);

        MTLSRuntimeConfig mtls = connection.mtls();

        if (mtls.clientCertPath().isPresent() || mtls.clientKeyPath().isPresent()) {
            throw new ConfigurationException(
                    "MTLS must be configured using quarkus.grpc.clients.temporal-client when using Quarkus managed gRPC channel");
        }

        return builder.build();
    }

    static RpcRetryOptions createRpcRetryOptions(RpcRetryRuntimeConfig rpcRetry) {
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

    private static Scope createScope(TemporalRuntimeConfig runtimeConfig, boolean isMicrometerEnabled) {
        Duration reportDuration = runtimeConfig.metricsReportInterval();
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
