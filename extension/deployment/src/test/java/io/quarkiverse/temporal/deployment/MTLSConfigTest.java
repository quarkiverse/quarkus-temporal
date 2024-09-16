package io.quarkiverse.temporal.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.quarkiverse.temporal.deployment.config.DefaultSimpleActivityImpl;
import io.quarkiverse.temporal.deployment.config.SimpleActivity;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.serviceclient.WorkflowServiceStubs;

public class MTLSConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(SimpleActivity.class)
                    .addClass(DefaultSimpleActivityImpl.class)
                    .addAsResource(
                            new StringAsset("quarkus.temporal.start-workers: false\n" +
                                    "quarkus.temporal.connection.mtls.client-cert-path: ./ca.pem\n" +
                                    "quarkus.temporal.connection.mtls.client-key-path: ./ca.key\n"),
                            "application.properties"));

    @Inject
    WorkflowServiceStubs workflowServiceStubs;

    @Test
    public void testMTLSConfiguration() {
        SslContext sslContext = workflowServiceStubs.getOptions().getSslContext();
        Assertions.assertNotNull(sslContext);
        Assertions.assertTrue(sslContext.isClient());
    }

}
