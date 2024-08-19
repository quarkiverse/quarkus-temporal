package io.quarkiverse.temporal.deployment.devui;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import io.quarkiverse.temporal.deployment.WorkerBuildItem;
import io.quarkiverse.temporal.deployment.WorkflowBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.ExternalPageBuilder;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.devui.spi.page.PageBuilder;
import io.quarkus.devui.spi.page.TableDataPageBuilder;
import io.temporal.client.WorkflowClient;

/**
 * Dev UI card for displaying important details such Temporal version.
 */
public class TemporalDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    void createCard(BuildProducer<CardPageBuildItem> cardPageBuildItemBuildProducer, List<WorkflowBuildItem> workflows,
            List<WorkerBuildItem> workers) {
        final CardPageBuildItem card = new CardPageBuildItem();

        final PageBuilder<ExternalPageBuilder> versionPage = Page.externalPageBuilder("Version")
                .icon("font-awesome-solid:book")
                .url("https://temporal.io")
                .doNotEmbed()
                .staticLabel(Objects.toString(WorkflowClient.class.getPackage().getImplementationVersion(), "?"));
        card.addPage(versionPage);

        final PageBuilder<TableDataPageBuilder> workflowsPage = Page.tableDataPageBuilder("Workflows")
                .icon("font-awesome-solid:arrow-right")
                .staticLabel(String.valueOf(workers.size()))
                .showColumn("name")
                .showColumn("workers")
                .buildTimeDataKey("workflows");

        card.addPage(workflowsPage);

        card.addBuildTimeData("workflows",
                workflows.stream().map(WorkflowBuildTimeData::new).collect(Collectors.toList()));

        final PageBuilder<TableDataPageBuilder> workersPage = Page.tableDataPageBuilder("Workers")
                .icon("font-awesome-solid:briefcase")
                .staticLabel(String.valueOf(workflows.size()))
                .showColumn("name")
                .showColumn("workflows")
                .showColumn("activities")
                .buildTimeDataKey("workers");

        card.addPage(workersPage);

        card.addBuildTimeData("workers",
                workers.stream().map(WorkerBuildTimeData::new).collect(Collectors.toList()));

        card.setCustomCard("qwc-temporal-card.js");

        cardPageBuildItemBuildProducer.produce(card);
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
                    .map(activities -> activities.getName().replaceAll("\\B\\w+(\\.[a-z])", "$1")).collect(Collectors.toList());
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
