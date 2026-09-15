package io.quarkiverse.temporal.deployment;

import java.time.Duration;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.temporal.ActivityStubInjector;
import io.quarkiverse.temporal.Constants;
import io.quarkiverse.temporal.deployment.stubinjection.ConstructorInjectedWorkflow;
import io.quarkiverse.temporal.deployment.stubinjection.ConstructorInjectedWorkflowImpl;
import io.quarkiverse.temporal.deployment.stubinjection.FieldInjectedWorkflow;
import io.quarkiverse.temporal.deployment.stubinjection.FieldInjectedWorkflowImpl;
import io.quarkiverse.temporal.deployment.stubinjection.InjectedActivity;
import io.quarkus.test.QuarkusUnitTest;
import io.temporal.worker.WorkerFactory;

public class ActivityStubInjectionTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(InjectedActivity.class)
                    .addClass(FieldInjectedWorkflow.class)
                    .addClass(FieldInjectedWorkflowImpl.class)
                    .addClass(ConstructorInjectedWorkflow.class)
                    .addClass(ConstructorInjectedWorkflowImpl.class)
                    .addAsResource(new StringAsset("quarkus.temporal.start-workers: false"), "application.properties"));

    @Inject
    WorkerFactory factory;

    @Test
    public void testWorkflowsWithInjectionPointsAreRegistered() {
        Assertions.assertNotNull(factory.tryGetWorker(Constants.DEFAULT_WORKER_NAME));
    }

    @Test
    public void testInjectionPointsAreDetected() {
        Assertions.assertTrue(ActivityStubInjector.hasInjectionPoints(FieldInjectedWorkflowImpl.class));
        Assertions.assertTrue(ActivityStubInjector.hasInjectionPoints(ConstructorInjectedWorkflowImpl.class));
        Assertions.assertEquals(FieldInjectedWorkflow.class,
                ActivityStubInjector.forClass(FieldInjectedWorkflowImpl.class).workflowInterface());
        Assertions.assertEquals(ConstructorInjectedWorkflow.class,
                ActivityStubInjector.forClass(ConstructorInjectedWorkflowImpl.class).workflowInterface());
    }

    @Test
    public void testDurationSyntax() throws Exception {
        var field = FieldInjectedWorkflowImpl.class.getDeclaredField("activity");
        var options = ActivityStubInjector
                .activityOptions(field.getAnnotation(io.quarkiverse.temporal.TemporalActivityStub.class));
        Assertions.assertEquals(Duration.ofSeconds(10), options.getStartToCloseTimeout());
    }
}
