package io.quarkiverse.temporal.test;

import io.temporal.testing.TestWorkflowEnvironment;

/**
 * Holds the {@link TestWorkflowEnvironment} currently prepared for the in-progress (or
 * about-to-start) test. Deliberately a plain static holder, not a CDI bean, so the
 * {@code @Dependent}-scoped {@code WorkerFactory} synthetic bean can read it at CDI
 * injection time - which happens at test-instance construction, before any JUnit
 * lifecycle callback has a chance to run.
 */
public final class MockTestEnvironmentHolder {

    private static volatile TestWorkflowEnvironment current;

    private MockTestEnvironmentHolder() {
    }

    public static TestWorkflowEnvironment current() {
        return current;
    }

    public static void set(TestWorkflowEnvironment environment) {
        current = environment;
    }
}
