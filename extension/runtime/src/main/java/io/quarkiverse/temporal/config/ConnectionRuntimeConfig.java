package io.quarkiverse.temporal.config;

import java.time.Duration;
import java.util.Optional;

import io.grpc.ManagedChannelBuilder;
import io.grpc.NameResolver;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ConnectionRuntimeConfig {

    /**
     * Sets a target string, which can be either a valid {@link NameResolver}-compliant URI, or an
     * authority string. See {@link ManagedChannelBuilder#forTarget(String)} for more information
     * about parameter format. Default is 127.0.0.1:7233
     */
    @WithDefault("127.0.0.1:7233")
    String target();

    /**
     * Sets option to enable SSL/ TLS/ HTTPS for gRPC.
     */
    @WithDefault("false")
    Boolean enableHttps();

    /**
     * Temporal Cloud API key is a unique identity linked to role-based access control (RBAC) settings to ensure secure and
     * appropriate access.
     */
    Optional<String> apiKey();

    /**
     * Rpc Retry Options.
     */
    RpcRetryRuntimeConfig rpcRetry();

    /**
     * mTLS Options.
     */
    MTLSRuntimeConfig mtls();

    /**
     * Sets the rpc timeout value for the following long poll based operations: PollWorkflowTaskQueue, PollActivityTaskQueue,
     * GetWorkflowExecutionHistory.
     * If not set uses Temporal default timeout of 70 seconds.
     */
    Optional<Duration> rpcLongPollTimeout();
}