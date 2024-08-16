package io.quarkiverse.temporal.config;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface WorkerBuildtimeConfig {

    /**
     * Set a unique identifier for this worker. The identifier should be stable with respect to the code the worker uses for
     * workflows, activities, and interceptors. For more information see: TODO: Doc link
     * A Build Id must be set if useBuildIdForVersioning is set true.
     * Defaults to the latest commit id
     */
    Optional<String> buildId();

    /**
     * Explicitly bind a workflow with this worker
     */
    Optional<List<String>> workflowClasses();

    /**
     * Explicitly bind a workflow with this worker
     */
    Optional<List<String>> activityClasses();
}
