package io.quarkiverse.temporal.deployment.devui;

import java.util.Map;

import org.testcontainers.utility.DockerImageName;

import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.dev.devservices.GlobalDevServicesConfig;
import io.quarkus.devservices.common.ContainerLocator;

@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = { GlobalDevServicesConfig.Enabled.class })
public class TemporalDevserviceProcessor {

    private static final Integer SERVER_EXPOSED_PORT = 7233;
    private static final Integer UI_EXPOSED_PORT = 8233;
    private static final String DEV_SERVICE_LABEL = "quarkus-devservice-temporal";
    private static final ContainerLocator containerLocator = new ContainerLocator(DEV_SERVICE_LABEL, SERVER_EXPOSED_PORT);

    @BuildStep
    public void build(TemporalDevserviceConfig config, BuildProducer<DevServicesResultBuildItem> devServiceProducer) {
        if (Boolean.FALSE.equals(config.enabled())) {
            return;
        }

        var imageStr = config.image() + ":" + config.version();
        var image = DockerImageName.parse(imageStr)
                .asCompatibleSubstituteFor(imageStr);

        var serverContainer = new TemporalContainer(image, config);
        serverContainer.start();

        var serverPort = serverContainer.getMappedPort(SERVER_EXPOSED_PORT);
        devServiceProducer.produce(new DevServicesResultBuildItem("temporal", serverContainer.getContainerId(), Map.of(
                "quarkus.temporal.connection.target", "localhost:" + serverPort)));
    }

}
