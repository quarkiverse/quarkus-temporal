package io.quarkiverse.temporal.deployment;

import java.util.List;

import jakarta.inject.Inject;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.client.WorkflowClient;
import io.temporal.opentracing.OpenTracingClientInterceptor;
import io.temporal.opentracing.OpenTracingWorkerInterceptor;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerFactoryOptions;

public class OtelTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-opentelemetry", Version.getVersion())))
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(
                            new StringAsset(
                                    "quarkus.temporal.start-workers: false\n"),
                            "application.properties"));

    @Inject
    WorkflowClient client;

    @Inject
    WorkerFactory factory;

    @Test
    public void testOtelInstrumentation() throws IllegalAccessException {
        Assertions.assertEquals(1, client.getOptions().getInterceptors().length);
        Assertions.assertInstanceOf(OpenTracingClientInterceptor.class, client.getOptions().getInterceptors()[0]);
        WorkerFactoryOptions factoryOptions = (WorkerFactoryOptions) FieldUtils.readField(factory, "factoryOptions", true);
        Assertions.assertEquals(1, factoryOptions.getWorkerInterceptors().length);
        Assertions.assertInstanceOf(OpenTracingWorkerInterceptor.class, factoryOptions.getWorkerInterceptors()[0]);
    }
}
