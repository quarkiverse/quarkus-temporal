package io.quarkiverse.temporal.deployment;

import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem.RunningDevService;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.dev.devservices.GlobalDevServicesConfig;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;


/**
 * Starts a Temporal service as dev service if needed.
 */
@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = GlobalDevServicesConfig.Enabled.class)
public class DevServicesTemporalProcessor {

    private static final String TEMPORAL_CONTAINER_NAME = "temporal";

    public DevServicesResultBuildItem createContainer(DockerStatusBuildItem dockerStatusBuildItem) {
        return startContainer(dockerStatusBuildItem).toBuildItem();
    }

    private RunningDevService startContainer(DockerStatusBuildItem dockerStatusBuildItem) {

        DockerImageName dockerImage = DockerImageName.parse("temporalio/server");
        TemporalServerContainer container = new TemporalServerContainer(dockerImage);
        container.start();

        return new RunningDevService(TEMPORAL_CONTAINER_NAME, container.getContainerId(),
                container::close, Map.of());
    }
}
