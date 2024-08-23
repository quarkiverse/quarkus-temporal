package io.quarkiverse.temporal.deployment;

import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.temporal.api.common.v1.Payload;
import io.temporal.client.WorkflowClient;
import io.temporal.common.context.ContextPropagator;

public class ContextPropagatorTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(TestContextPropagator.class)
                    .addAsResource(new StringAsset("quarkus.temporal.start-workers: false\n" +
                            "quarkus.temporal.context-propagator-classes[0]=io.quarkiverse.temporal.deployment.ContextPropagatorTest$TestContextPropagator\n"),
                            "application.properties"));

    @Inject
    WorkflowClient client;

    @Test
    public void testCustomClient() {
        Assertions.assertEquals(1, client.getOptions().getContextPropagators().size());
    }

    public static class TestContextPropagator implements ContextPropagator {
        @Override
        public String getName() {
            return this.getClass().getName();
        }

        @Override
        public Map<String, Payload> serializeContext(Object context) {
            return Map.of();
        }

        @Override
        public Object deserializeContext(Map<String, Payload> context) {
            return null;
        }

        @Override
        public Object getCurrentContext() {
            return null;
        }

        @Override
        public void setCurrentContext(Object context) {
        }
    }
}
