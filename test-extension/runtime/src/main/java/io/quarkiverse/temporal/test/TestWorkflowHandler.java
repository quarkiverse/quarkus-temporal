package io.quarkiverse.temporal.test;

import java.util.Optional;

import io.quarkiverse.temporal.WorkflowClientOptionsSupport;
import io.quarkus.arc.BeanCreator;
import io.quarkus.arc.SyntheticCreationalContext;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.testing.TestEnvironmentOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.WorkerFactory;

/**
 * Creates the mock-mode synthetic beans: the {@link TestWorkflowEnvironment}, and the {@link WorkflowClient} and
 * {@link WorkerFactory} backed by it.
 */
public final class TestWorkflowHandler {

    private TestWorkflowHandler() {
    }

    /**
     * Creates the {@link TestWorkflowEnvironment} bean.
     */
    public static class EnvironmentCreator implements BeanCreator<TestWorkflowEnvironment> {

        @Override
        public TestWorkflowEnvironment create(SyntheticCreationalContext<TestWorkflowEnvironment> context) {
            TestEnvironmentOptions options = TestEnvironmentOptions.newBuilder()
                    .setWorkflowClientOptions(createTestWorkflowClientOptions(context))
                    .build();

            TestWorkflowEnvironment environment = TestWorkflowEnvironment.newInstance(options);
            // Seed the holder immediately: the @Dependent WorkerFactory bean (and the
            // first test's MockTestWorkflowResetCallback) both read from it.
            MockTestEnvironmentHolder.set(environment);
            return environment;
        }
    }

    /**
     * Creates the {@link WorkflowClient} bean from the {@link TestWorkflowEnvironment}.
     */
    public static class WorkflowClientCreator implements BeanCreator<WorkflowClient> {

        @Override
        public WorkflowClient create(SyntheticCreationalContext<WorkflowClient> context) {
            TestWorkflowEnvironment testWorkflowEnvironment = context.getInjectedReference(TestWorkflowEnvironment.class);
            return testWorkflowEnvironment.getWorkflowClient();
        }
    }

    /**
     * Creates the {@link WorkerFactory} bean from the current {@link TestWorkflowEnvironment}.
     */
    public static class WorkerFactoryCreator implements BeanCreator<WorkerFactory> {

        @Override
        public WorkerFactory create(SyntheticCreationalContext<WorkerFactory> context) {
            // TestWorkflowEnvironment is ApplicationScoped (proxied): merely obtaining the
            // injected reference does not trigger its creation, only invoking a method on it
            // does. That first creation is what seeds MockTestEnvironmentHolder, which is
            // then read below (not the value returned here) so later tests - which swap the
            // holder directly - are picked up too.
            TestWorkflowEnvironment testWorkflowEnvironment = context.getInjectedReference(TestWorkflowEnvironment.class);
            testWorkflowEnvironment.getWorkerFactory();
            return MockTestEnvironmentHolder.current().getWorkerFactory();
        }
    }

    /**
     * Builds the {@link WorkflowClientOptions} used by the mock TestWorkflowEnvironment, while honoring the CDI wiring.
     */
    public static WorkflowClientOptions createTestWorkflowClientOptions(SyntheticCreationalContext<?> context) {
        return WorkflowClientOptionsSupport.buildFromContext(
                context,
                "default",
                Optional.empty());
    }
}
