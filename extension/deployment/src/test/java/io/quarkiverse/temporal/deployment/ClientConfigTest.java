package io.quarkiverse.temporal.deployment;

import static io.grpc.Status.Code.NOT_FOUND;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.deployment.config.DefaultSimpleActivityImpl;
import io.quarkiverse.temporal.deployment.config.SimpleActivity;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.RpcRetryOptions;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

public class ClientConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleActivity.class)
                    .addClass(DefaultSimpleActivityImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n" +
                                    "quarkus.temporal.identity: customIdentity\n" +
                                    "quarkus.temporal.namespace: customNamespace\n" +
                                    "quarkus.temporal.connection.target: customTarget:1234\n" +
                                    "quarkus.temporal.connection.enable-https: true\n" +
                                    "quarkus.temporal.connection.rpc-retry.initial-interval: 7s\n" +
                                    "quarkus.temporal.connection.rpc-retry.congestion-initial-interval: 11s\n" +
                                    "quarkus.temporal.connection.rpc-retry.expiration: 13m\n" +
                                    "quarkus.temporal.connection.rpc-retry.backoff-coefficient: 17\n" +
                                    "quarkus.temporal.connection.rpc-retry.maximum-attempts: 19\n" +
                                    "quarkus.temporal.connection.rpc-retry.maximum-interval: 23s\n" +
                                    "quarkus.temporal.connection.rpc-retry.maximum-jitter-coefficient: 0.29\n" +
                                    "quarkus.temporal.connection.rpc-retry.do-not-retry[0]: NOT_FOUND\n" +
                                    "quarkus.temporal.connection.rpc-long-poll-timeout: 10s\n"),
                            "application.properties"));

    @Inject
    WorkflowClient client;

    @Test
    public void testClientConfiguration() {
        WorkflowClientOptions options = client.getOptions();
        Assertions.assertNotNull(options);
        Assertions.assertEquals("customIdentity", options.getIdentity());
        Assertions.assertEquals("customNamespace", options.getNamespace());
    }

    @Test
    public void testWorkflowServiceStubsConfiguration() {
        WorkflowServiceStubsOptions options = client.getWorkflowServiceStubs().getOptions();
        Assertions.assertEquals("customTarget:1234", options.getTarget());
        Assertions.assertEquals(Duration.of(10, ChronoUnit.SECONDS), options.getRpcLongPollTimeout());
        Assertions.assertTrue(options.getEnableHttps());
    }

    @Test
    public void testRpcRetryConfiguration() {
        RpcRetryOptions options = client.getWorkflowServiceStubs().getOptions().getRpcRetryOptions();
        Assertions.assertEquals(Duration.of(7, ChronoUnit.SECONDS), options.getInitialInterval());
        Assertions.assertEquals(Duration.of(11, ChronoUnit.SECONDS), options.getCongestionInitialInterval());
        Assertions.assertEquals(Duration.of(13, ChronoUnit.MINUTES), options.getExpiration());
        Assertions.assertEquals(17, options.getBackoffCoefficient());
        Assertions.assertEquals(19, options.getMaximumAttempts());
        Assertions.assertEquals(Duration.of(23, ChronoUnit.SECONDS), options.getMaximumInterval());
        Assertions.assertEquals(0.29, options.getMaximumJitterCoefficient());
        Assertions.assertEquals(1, options.getDoNotRetry().size());
        Assertions.assertEquals(NOT_FOUND, options.getDoNotRetry().get(0).getCode());

    }
}
