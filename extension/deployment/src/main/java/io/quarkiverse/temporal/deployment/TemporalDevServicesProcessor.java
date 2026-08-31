package io.quarkiverse.temporal.deployment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.google.protobuf.Duration;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkiverse.temporal.config.TemporalBuildtimeConfig;
import io.quarkiverse.temporal.config.TemporalDevServicesConfig;
import io.quarkus.deployment.IsDevServicesSupportedByLaunchMode;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.Startable;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.devservices.common.ConfigureUtil;
import io.quarkus.devservices.common.ContainerAddress;
import io.quarkus.devservices.common.ContainerLocator;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.configuration.ConfigUtils;
import io.temporal.api.workflowservice.v1.DescribeNamespaceRequest;
import io.temporal.api.workflowservice.v1.RegisterNamespaceRequest;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

/**
 * Starts a local Temporal development server for dev mode and tests when an application did not configure one.
 */
@BuildSteps(onlyIf = { IsDevServicesSupportedByLaunchMode.class, DevServicesConfig.Enabled.class })
public class TemporalDevServicesProcessor {

    public static final String FEATURE = "temporal";
    public static final String CONNECTION_TARGET = "quarkus.temporal.connection.target";
    public static final String WEB_UI_URL = "quarkus.temporal.devservices.web-ui-url";

    private static final int GRPC_PORT = 7233;
    private static final int UI_PORT = 8233;
    private static final Duration NAMESPACE_RETENTION = Duration.newBuilder().setSeconds(24 * 60 * 60).build();
    private static final ContainerLocator GRPC_CONTAINER_LOCATOR = new ContainerLocator(FEATURE, GRPC_PORT);
    private static final ContainerLocator UI_CONTAINER_LOCATOR = new ContainerLocator(FEATURE, UI_PORT);

    @BuildStep
    DevServicesResultBuildItem startTemporal(
            TemporalDevServicesConfig devServicesConfig,
            TemporalBuildtimeConfig temporalBuildtimeConfig,
            LaunchModeBuildItem launchMode) {
        if (!devServicesConfig.enabled()
                || temporalBuildtimeConfig.enableMock()
                || ConfigUtils.isPropertyPresent(CONNECTION_TARGET)) {
            return null;
        }

        Optional<ContainerAddress> existingTemporal = GRPC_CONTAINER_LOCATOR.locateContainer(
                devServicesConfig.serviceName(), devServicesConfig.shared(), launchMode.getLaunchMode());
        if (existingTemporal.isPresent()) {
            ContainerAddress grpcAddress = existingTemporal.get();
            provisionNamespaces(grpcAddress.getUrl(), devServicesConfig.namespaces().orElse(List.of()));
            Map<String, String> config = new HashMap<>();
            config.put(CONNECTION_TARGET, grpcAddress.getUrl());
            UI_CONTAINER_LOCATOR.locateContainer(devServicesConfig.serviceName(), devServicesConfig.shared(),
                    launchMode.getLaunchMode())
                    .ifPresent(uiAddress -> config.put(WEB_UI_URL, "http://" + uiAddress.getUrl()));

            return DevServicesResultBuildItem.discovered()
                    .feature(FEATURE)
                    .containerId(grpcAddress.getId())
                    .config(config)
                    .build();
        }

        return DevServicesResultBuildItem.owned()
                .feature(FEATURE)
                .serviceName(devServicesConfig.serviceName())
                .serviceConfig(devServicesConfig)
                .startable(() -> new TemporalContainer(launchMode.getLaunchMode(), devServicesConfig.shared(),
                        devServicesConfig.reuse(),
                        devServicesConfig.serviceName(), devServicesConfig.imageName(), devServicesConfig.port(),
                        devServicesConfig.namespaces().orElse(List.of())))
                .configProvider(Map.of(
                        CONNECTION_TARGET, TemporalContainer::getConnectionInfo,
                        WEB_UI_URL, TemporalContainer::getWebUiUrl))
                .build();
    }

    static final class TemporalContainer extends GenericContainer<TemporalContainer> implements Startable {

        private final List<String> namespaces;
        private final boolean keepRunning;

        TemporalContainer(LaunchMode launchMode, boolean shared, boolean reuse, String serviceName, String imageName,
                java.util.OptionalInt fixedPort, List<String> namespaces) {
            super(DockerImageName.parse(imageName));
            this.namespaces = normalizeNamespaces(namespaces);
            this.keepRunning = shared && launchMode == LaunchMode.DEVELOPMENT;
            withCommand("server", "start-dev", "--ip", "0.0.0.0");
            ConfigureUtil.configureSharedServiceLabel(this, launchMode, FEATURE, serviceName);
            withReuse(keepRunning && reuse);
            withExposedPorts(GRPC_PORT, UI_PORT);
            fixedPort.ifPresent(port -> addFixedExposedPort(port, GRPC_PORT));
            waitingFor(Wait.forListeningPort());
        }

        @Override
        public void start() {
            super.start();
            provisionNamespaces(getConnectionInfo(), namespaces);
        }

        @Override
        public String getConnectionInfo() {
            return getHost() + ":" + getMappedPort(GRPC_PORT);
        }

        String getWebUiUrl() {
            return "http://" + getHost() + ":" + getMappedPort(UI_PORT);
        }

        @Override
        public void close() {
            if (!keepRunning) {
                super.close();
            }
        }

    }

    private static List<String> normalizeNamespaces(List<String> namespaces) {
        return namespaces.stream()
                .map(String::trim)
                .filter(namespace -> !namespace.isEmpty())
                .filter(namespace -> !"default".equals(namespace))
                .distinct()
                .toList();
    }

    static void provisionNamespaces(String target, List<String> namespaces) {
        List<String> namespacesToProvision = normalizeNamespaces(namespaces);
        if (namespacesToProvision.isEmpty()) {
            return;
        }

        WorkflowServiceStubs stubs = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
        try {
            namespacesToProvision.forEach(namespace -> createNamespace(stubs, namespace));
        } finally {
            stubs.shutdown();
        }
    }

    private static void createNamespace(WorkflowServiceStubs stubs, String namespace) {
        try {
            stubs.blockingStub().describeNamespace(
                    DescribeNamespaceRequest.newBuilder().setNamespace(namespace).build());
            return;
        } catch (StatusRuntimeException exception) {
            if (exception.getStatus().getCode() != Status.Code.NOT_FOUND) {
                throw new IllegalStateException("Unable to check Temporal namespace '" + namespace + "'", exception);
            }
        }

        try {
            stubs.blockingStub().registerNamespace(
                    RegisterNamespaceRequest.newBuilder()
                            .setNamespace(namespace)
                            .setWorkflowExecutionRetentionPeriod(NAMESPACE_RETENTION)
                            .build());
        } catch (StatusRuntimeException exception) {
            if (exception.getStatus().getCode() != Status.Code.ALREADY_EXISTS) {
                throw new IllegalStateException("Unable to create Temporal namespace '" + namespace + "'", exception);
            }
        }
    }
}
