package io.quarkiverse.temporal.deployment.devui;

import java.util.Objects;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.ExternalPageBuilder;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.devui.spi.page.PageBuilder;
import io.temporal.client.WorkflowClient;

/**
 * Dev UI card for displaying important details such Temporal version.
 */
public class TemporalDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    void createCard(BuildProducer<CardPageBuildItem> cardPageBuildItemBuildProducer) {
        final CardPageBuildItem card = new CardPageBuildItem();

        final PageBuilder<ExternalPageBuilder> versionPage = Page.externalPageBuilder("Version")
                .icon("font-awesome-solid:book")
                .url("https://temporal.io")
                .doNotEmbed()
                .staticLabel(Objects.toString(WorkflowClient.class.getPackage().getImplementationVersion(), "?"));
        card.addPage(versionPage);

        card.setCustomCard("qwc-temporal-card.js");

        cardPageBuildItemBuildProducer.produce(card);
    }
}