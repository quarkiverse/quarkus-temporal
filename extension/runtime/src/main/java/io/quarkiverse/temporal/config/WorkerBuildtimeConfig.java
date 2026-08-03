package io.quarkiverse.temporal.config;

import java.util.List;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface WorkerBuildtimeConfig {

    /**
     * Assigns a unique identifier to this worker. The identifier must remain consistent with the code the worker
     * utilizes for workflows, activities, and interceptors. For further details, refer to:
     * <a href=
     * "https://docs.temporal.io/develop/java/versioning#assign-a-build-id-to-your-worker-and-opt-in-to-worker-versioning">Temporal
     * Build ID</a>.
     * A Build ID is required if `useBuildIdForVersioning` is set to true.
     * By default, the latest Git commit ID is used.
     *
     * @deprecated Use {@code quarkus.temporal.worker.deployment-options.build-id} of
     *             <a href="https://docs.temporal.io/worker-deployments">Worker Deployment API</a> to configure the buildId.
     *             If {@code quarkus.temporal.worker.deployment-options} is configured, this property is ignored.
     */
    @Deprecated
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