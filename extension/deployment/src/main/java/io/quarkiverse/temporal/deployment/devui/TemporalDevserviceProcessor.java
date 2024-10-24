package io.quarkiverse.temporal.deployment.devui;

import java.util.Map;

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
import io.quarkus.vertx.core.deployment.CoreVertxBuildItem;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.runtime.management.ManagementInterfaceBuildTimeConfig;

@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = GlobalDevServicesConfig.Enabled.class)
public class TemporalDevserviceProcessor {

    @BuildStep
    DevServicesResultBuildItem start(TemporalDevserviceConfig config,
            NonApplicationRootPathBuildItem nonApplicationRootPathBuildItem,
            LaunchModeBuildItem launchMode,
            ManagementInterfaceBuildTimeConfig managementInterfaceBuildTimeConfig) {
        if (Boolean.FALSE.equals(config.enabled())) {
            return null;
        }

        String path = nonApplicationRootPathBuildItem.resolveManagementPath(
                TemporalProcessor.FEATURE,
                managementInterfaceBuildTimeConfig,
                launchMode);

        TemporalContainer container = new TemporalContainer(config.image(), path, config.reuse());
        container.start();

        Map<String, String> configOverrides = Map.of(
                "quarkus.temporal.connection.target", container.getServerUrl(),
                "quarkus.temporal.ui.url", container.getUiUrl(),
                "quarkus.otel.instrument.grpc", "false",
                "quarkus.temporal.telemetry.enabled", "false",
                "quarkus.grpc.server.use-separate-server", "false",
                "quarkus.otel.instrument.vertx-http", "false");

        return new DevServicesResultBuildItem.RunningDevService(
                TemporalProcessor.FEATURE,
                container.getContainerId(),
                container::close,
                configOverrides)
                .toBuildItem();
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerWebProxy(
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
