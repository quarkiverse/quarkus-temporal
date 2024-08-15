package io.quarkiverse.temporal.config;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface WorkerBuildtimeConfig {

    /**
     * Explicitly bind a workflow with this worker
     */
    Optional<List<String>> workflowClasses();

    /**
     * Explicitly bind a workflow with this worker
     */
    Optional<List<String>> activityClasses();
}
