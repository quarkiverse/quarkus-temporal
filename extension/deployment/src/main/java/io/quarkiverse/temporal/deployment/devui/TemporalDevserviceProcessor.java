package io.quarkiverse.temporal.deployment.devui;

import java.util.Map;

import org.testcontainers.utility.DockerImageName;

import io.quarkiverse.temporal.deployment.TemporalProcessor;
import io.quarkiverse.temporal.devui.TemporalUiProxy;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.dev.devservices.GlobalDevServicesConfig;
import io.quarkus.devservices.common.ContainerLocator;
import io.quarkus.vertx.core.deployment.CoreVertxBuildItem;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.runtime.management.ManagementInterfaceBuildTimeConfig;

@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = { GlobalDevServicesConfig.Enabled.class })
public class TemporalDevserviceProcessor {

    private static final Integer SERVER_EXPOSED_PORT = 7233;
    private static final Integer UI_EXPOSED_PORT = 8233;
    private static final String DEV_SERVICE_LABEL = "quarkus-devservice-temporal";
    private static final ContainerLocator containerLocator = new ContainerLocator(DEV_SERVICE_LABEL, SERVER_EXPOSED_PORT);

    @BuildStep
    void build(
            TemporalDevserviceConfig config,
            BuildProducer<DevServicesResultBuildItem> devServiceProducer,
            ManagementInterfaceBuildTimeConfig managementInterfaceBuildTimeConfig,
            LaunchModeBuildItem launchMode,
            NonApplicationRootPathBuildItem nonApplicationRootPathBuildItem) {
        if (Boolean.FALSE.equals(config.enabled())) {
            return;
        }

        var path = nonApplicationRootPathBuildItem.resolveManagementPath(
                TemporalProcessor.FEATURE,
                managementInterfaceBuildTimeConfig,
                launchMode);

        var imageStr = config.image() + ":" + config.version();
        var image = DockerImageName.parse(imageStr)
                .asCompatibleSubstituteFor(imageStr);

        var serverContainer = new TemporalContainer(image, config, path, DEV_SERVICE_LABEL);
        serverContainer.start();

        var serverPort = serverContainer.getMappedPort(SERVER_EXPOSED_PORT);
        devServiceProducer.produce(new DevServicesResultBuildItem("temporal", serverContainer.getContainerId(), Map.of(
                "quarkus.temporal.connection.target", "localhost:" + serverPort,
                "quarkus.temporal.ui.url", serverContainer.getUiUrl(),
                "quarkus.temporal.ui.port", serverContainer.getMappedPort(UI_EXPOSED_PORT).toString())));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerProxy(
            TemporalDevserviceConfig config,
            TemporalUiProxy proxy,
            BuildProducer<RouteBuildItem> routes,
            NonApplicationRootPathBuildItem frameworkRoot,
            CoreVertxBuildItem coreVertxBuildItem) {
        if (Boolean.FALSE.equals(config.enabled())) {
            return;
        }

        routes.produce(frameworkRoot.routeBuilder()
                .management()
                .route(TemporalProcessor.FEATURE + "/*")
                .displayOnNotFoundPage("Portal UI not found")
                .handler(proxy.handler(coreVertxBuildItem.getVertx()))
                .build());
    }

}
