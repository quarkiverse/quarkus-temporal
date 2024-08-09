package io.quarkiverse.temporal.test.deployment;

import io.quarkus.builder.item.SimpleBuildItem;
import io.temporal.testing.TestWorkflowEnvironment;

public final class TestWorkflowEnvironmentBuildItem extends SimpleBuildItem {

    public final TestWorkflowEnvironment testWorkflowEnvironment;

    public TestWorkflowEnvironmentBuildItem(TestWorkflowEnvironment testWorkflowEnvironment) {
        this.testWorkflowEnvironment = testWorkflowEnvironment;
    }
}
