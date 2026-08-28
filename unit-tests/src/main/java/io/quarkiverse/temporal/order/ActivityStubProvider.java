package io.quarkiverse.temporal.order;

import java.time.Duration;

import org.jboss.resteasy.reactive.ClientWebApplicationException;

import com.fasterxml.jackson.databind.JsonMappingException;

import io.quarkiverse.temporal.order.activities.*;
import io.quarkiverse.temporal.order.model.exceptions.*;
import io.quarkus.arc.ArcUndeclaredThrowableException;
import io.temporal.activity.ActivityCancellationType;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.common.converter.DataConverterException;
import io.temporal.workflow.Workflow;

public class ActivityStubProvider {

    private static final String orderTaskQueue = "order-entity-tasks";

    private static final ActivityOptions options = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setDoNotRetry(
                            ArcUndeclaredThrowableException.class.getName(),
                            JsonMappingException.class.getName(),
                            NullPointerException.class.getName(),
                            IllegalArgumentException.class.getName(),
                            ClientWebApplicationException.class.getName(),
                            DataConverterException.class.getName(),
                            // Custom exceptions go here
                            OrderInvalidException.class.getName(),
                            OrderNotFoundException.class.getName(),
                            PaymentDeclinedException.class.getName(),
                            MissingItemException.class.getName(),
                            CustomerHistoryException.class.getName())
                    .setInitialInterval(Duration.ofSeconds(1))
                    .setMaximumInterval(Duration.ofSeconds(100))
                    .setBackoffCoefficient(2)
                    .setMaximumAttempts(500)
                    .build())
            .build();

    public static LocalConfigActivities getLocalConfigActivitiys() {
        ActivityOptions newOptions = ActivityOptions.newBuilder(options)
                .setTaskQueue(orderTaskQueue)
                .setCancellationType(ActivityCancellationType.WAIT_CANCELLATION_COMPLETED)
                .build();
        return Workflow.newActivityStub(
                LocalConfigActivities.class,
                newOptions);
    }

    public static OrderValidationActivities getOrderValidationActivities() {
        ActivityOptions newOptions = ActivityOptions.newBuilder(options)
                .setTaskQueue(orderTaskQueue)
                .setCancellationType(ActivityCancellationType.WAIT_CANCELLATION_COMPLETED)
                .build();

        return Workflow.newActivityStub(
                OrderValidationActivities.class,
                newOptions);
    }

    public static PaymentValidationActivities getPaymentValidationActivities() {
        ActivityOptions newOptions = ActivityOptions.newBuilder(options)
                .setTaskQueue(orderTaskQueue)
                .setCancellationType(ActivityCancellationType.WAIT_CANCELLATION_COMPLETED)
                .build();

        return Workflow.newActivityStub(
                PaymentValidationActivities.class,
                newOptions);

    }

    public static OrderSupplementalActivities getOrderSupplementalActivities() {
        ActivityOptions newOptions = ActivityOptions.newBuilder(options)
                .setTaskQueue(orderTaskQueue)
                .setCancellationType(ActivityCancellationType.WAIT_CANCELLATION_COMPLETED)
                .build();

        return Workflow.newActivityStub(
                OrderSupplementalActivities.class,
                newOptions);

    }

    public static OrderEnrichmentActivities getOrderEnrichmentActivities() {
        ActivityOptions newOptions = ActivityOptions.newBuilder(options)
                .setTaskQueue(orderTaskQueue)
                .setCancellationType(ActivityCancellationType.WAIT_CANCELLATION_COMPLETED)
                .build();

        return Workflow.newActivityStub(
                OrderEnrichmentActivities.class,
                newOptions);

    }

}
