package io.quarkiverse.temporal.deployment.devui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import io.quarkiverse.temporal.deployment.TemporalProcessor;
import io.quarkiverse.temporal.deployment.WorkerBuildItem;
import io.quarkiverse.temporal.deployment.WorkflowBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.dev.devservices.GlobalDevServicesConfig;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.ExternalPageBuilder;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.devui.spi.page.PageBuilder;
import io.quarkus.devui.spi.page.TableDataPageBuilder;
import io.quarkus.vertx.http.deployment.NonApplicationRootPathBuildItem;
import io.quarkus.vertx.http.runtime.management.ManagementInterfaceBuildTimeConfig;
import io.temporal.client.WorkflowClient;

/**
 * Dev UI card for displaying important details such Temporal version.
 */
@BuildSteps(onlyIf = IsDevelopment.class)
public class TemporalDevUIProcessor {

    @BuildStep
    void createCard(
            BuildProducer<CardPageBuildItem> cardPageBuildItemBuildProducer,
            List<WorkflowBuildItem> workflows,
            List<WorkerBuildItem> workers,
            GlobalDevServicesConfig globalDevServicesConfig,
            TemporalUiConfig uiConfig,
            TemporalDevserviceConfig temporalDevserviceConfig,
            ManagementInterfaceBuildTimeConfig managementInterfaceBuildTimeConfig,
            LaunchModeBuildItem launchMode,
            NonApplicationRootPathBuildItem nonApplicationRootPathBuildItem) {
        final CardPageBuildItem card = new CardPageBuildItem();

        final PageBuilder<ExternalPageBuilder> versionPage = Page.externalPageBuilder("Version")
                .icon("font-awesome-solid:book")
                .url("https://temporal.io")
                .doNotEmbed()
                .staticLabel(Objects.toString(WorkflowClient.class.getPackage().getImplementationVersion(), "?"));
        card.addPage(versionPage);

        final PageBuilder<TableDataPageBuilder> workflowsPage = Page.tableDataPageBuilder("Workflows")
                .icon("font-awesome-solid:arrow-right")
                .staticLabel(String.valueOf(workflows.size()))
                .showColumn("name")
                .showColumn("workers")
                .buildTimeDataKey("workflows");

        card.addPage(workflowsPage);

        card.addBuildTimeData("workflows",
                workflows.stream().map(WorkflowBuildTimeData::new).collect(Collectors.toList()));

        final PageBuilder<TableDataPageBuilder> workersPage = Page.tableDataPageBuilder("Workers")
                .icon("font-awesome-solid:briefcase")
                .staticLabel(String.valueOf(workers.size()))
                .showColumn("name")
                .showColumn("workflows")
                .showColumn("activities")
                .buildTimeDataKey("workers");

        card.addPage(workersPage);

        card.addBuildTimeData("workers",
                workers.stream().map(WorkerBuildTimeData::new).collect(Collectors.toList()));

        uiPage(uiConfig.url(), temporalDevserviceConfig, managementInterfaceBuildTimeConfig, launchMode,
                nonApplicationRootPathBuildItem, card);

        card.setCustomCard("qwc-temporal-card.js");

        cardPageBuildItemBuildProducer.produce(card);
    }

    private void uiPage(
            Optional<String> configPath,
            TemporalDevserviceConfig temporalDevserviceConfig,
            ManagementInterfaceBuildTimeConfig managementInterfaceBuildTimeConfig,
            LaunchModeBuildItem launchMode,
            NonApplicationRootPathBuildItem nonApplicationRootPathBuildItem,
            CardPageBuildItem card) {
        var path = configPath;

        // check if the UI url is set in the config or if the devservice is enabled
        if (!path.isPresent() && Boolean.TRUE.equals(temporalDevserviceConfig.enabled())) {
            var defaultBasePath = nonApplicationRootPathBuildItem.resolveManagementPath(
                    TemporalProcessor.FEATURE,
                    managementInterfaceBuildTimeConfig,
                    launchMode);

            path = Optional.of(defaultBasePath);
        }

        // if the path is not set, we don't have a UI to link to
        if (!path.isPresent()) {
            return;
        }

        // add the UI page
        final PageBuilder<ExternalPageBuilder> uiPage = Page.externalPageBuilder("UI")
                .icon("font-awesome-solid:desktop")
                .url(path.get(), path.get())
                .isHtmlContent();

        card.addPage(uiPage);
    }

    static class WorkflowBuildTimeData {
        WorkflowBuildTimeData(WorkflowBuildItem item) {
            this.name = item.workflow.getName().replaceAll("\\B\\w+(\\.[a-z])", "$1");
            this.workers = item.workers;
        }

        private final String name;
        private final String[] workers;

        public String[] getWorkers() {
            return workers;
        }

        public String getName() {
            return name;
        }
    }

    static class WorkerBuildTimeData {
        WorkerBuildTimeData(WorkerBuildItem item) {

            this.name = item.name;
            this.workflows = item.workflows.stream().map(workflow -> workflow.getName().replaceAll("\\B\\w+(\\.[a-z])", "$1"))
                    .collect(Collectors.toList());
            this.activities = item.activities.stream()
                    .map(activity -> activity.getName().replaceAll("\\B\\w+(\\.[a-z])", "$1")).collect(Collectors.toList());
        }

        private final String name;
        private final List<String> workflows;
        private final List<String> activities;

        public List<String> getWorkflows() {
            return workflows;
        }

        public List<String> getActivities() {
            return activities;
        }

        public String getName() {
            return name;
        }
    }

}