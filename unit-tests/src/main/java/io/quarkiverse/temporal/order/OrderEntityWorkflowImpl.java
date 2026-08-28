package io.quarkiverse.temporal.order;

import java.util.ArrayDeque;
import java.util.List;

import org.slf4j.Logger;

import io.quarkiverse.temporal.order.activities.*;
import io.quarkiverse.temporal.order.model.customer.OrderEntityResponse;
import io.quarkiverse.temporal.order.model.customer.OrderValidationResponse;
import io.quarkiverse.temporal.order.model.customer.PaymentInfo;
import io.quarkiverse.temporal.order.model.exceptions.MissingItemException;
import io.quarkiverse.temporal.order.model.exceptions.OrderInvalidException;
import io.quarkiverse.temporal.order.model.exceptions.OrderNotFoundException;
import io.quarkiverse.temporal.order.model.exceptions.PaymentDeclinedException;
import io.quarkiverse.temporal.order.model.order.input.OrderInit;
import io.quarkiverse.temporal.order.model.order.input.OrderInput;
import io.quarkiverse.temporal.order.model.order.payment.Metadata;
import io.quarkiverse.temporal.order.model.order.payment.PaymentValidationRequest;
import io.quarkiverse.temporal.order.model.order.state.OrderItem;
import io.quarkiverse.temporal.order.model.order.state.OrderState;
import io.quarkiverse.temporal.order.model.workflow.OrderEntityConfig;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.*;

public class OrderEntityWorkflowImpl implements OrderEntityWorkflow {
    private final Logger log = Workflow.getLogger(OrderEntityWorkflowImpl.class);

    private final LocalConfigActivities localConfigActivities = ActivityStubProvider.getLocalConfigActivitiys();
    private final OrderValidationActivities orderValidationActivities = ActivityStubProvider.getOrderValidationActivities();
    private final PaymentValidationActivities paymentValidationActivities = ActivityStubProvider
            .getPaymentValidationActivities();
    private final OrderSupplementalActivities orderSupplementalActivities = ActivityStubProvider
            .getOrderSupplementalActivities();
    private final OrderEnrichmentActivities orderEnrichmentActivities = ActivityStubProvider.getOrderEnrichmentActivities();

    private OrderState orderState = OrderState.empty();
    private final ArrayDeque<PaymentInfo> unprocessedPayments = new ArrayDeque<>();

    OrderEntityConfig config;

    private boolean orderExpired = false;
    private boolean orderCanceled = false;
    private boolean orderExit = false;

    @WorkflowInit
    public OrderEntityWorkflowImpl(OrderInit orderInit) {
        orderState = orderState.withOrderInit(orderInit, Workflow.currentTimeMillis());
    }

    @Override
    public String create(OrderInit orderInit) {
        if (log.isDebugEnabled()) {
            log.debug("create called with {}", orderInit);
        }
        log.info("create called with {}", orderInit);

        // pick up configuration
        config = localConfigActivities.getEntityConfig();

        if (log.isDebugEnabled()) {
            log.debug("config values: maxPollingAwaitTime: {}, orderExpiryDuration: {}", config.maxPollingAwaitTime(),
                    config.orderExpiryDuration());
        }
        log.info("config values: maxPollingAwaitTime: {}, orderExpiryDuration: {}", config.maxPollingAwaitTime(),
                config.orderExpiryDuration());

        // start the order expiration timer
        Workflow.newTimer(
                config.orderExpiryDuration(),
                TimerOptions.newBuilder().setSummary("Workflow Order Expiry").build())
                .thenApply(notUsed -> orderExpired = true);

        // await signals (order, payment) or expiry
        do {
            Workflow.await(config.maxPollingAwaitTime(), () -> orderCanceled
                    || orderExpired
                    || orderExit
                    || orderState.orderStatus() == OrderState.OrderStatus.ORDER_RECEIVED
                    || (orderState.orderStatus() == OrderState.OrderStatus.VALID && !unprocessedPayments.isEmpty()));

            log.info("control loop: order status: {}, order amount {}", orderState.orderStatus(),
                    orderState.orderTotal().longValue());

            if (orderState.orderStatus() == OrderState.OrderStatus.ORDER_RECEIVED) {
                processOrderReceived();
            }

            // need to wait until we have the order and calculated the order amount
            if (orderState.orderTotal().longValue() > 0) {
                while (!unprocessedPayments.isEmpty()) {
                    processPayment(unprocessedPayments.removeFirst());
                }
            }
        } while (!orderCanceled
                && !orderExpired
                && !orderExit
                && !(orderState.orderStatus() == OrderState.OrderStatus.VALID && orderState.RRN().isPresent()));

        // it's possible the order expired, but we've already satisfied the order ready for fulfillment
        if (orderState.orderStatus() == OrderState.OrderStatus.VALID && orderState.RRN().isPresent()) {
            orderState = orderState.withStatus(OrderState.OrderStatus.FULFILLMENT);
        } else if (orderCanceled) {
            orderState = orderState.withStatus(OrderState.OrderStatus.CANCELED);
        } else if (orderExpired) {
            orderState = orderState.withStatus(OrderState.OrderStatus.EXPIRED);
        }

        return String.format(
                "The order workflow completed for Customer ID: %s Order ID: %s, order canceled %s, order expired %s, order status %s",
                orderState.customerId(), orderState.orderId(), orderCanceled, orderExpired,
                orderState.orderStatus().toString());
    }

    @Override
    public void orderInput(OrderInput orderInput) {
        if (log.isDebugEnabled()) {
            log.debug("order input signal received: {}", orderInput);
        }
        orderState = orderState.withOrderInput(orderInput);
    }

    @Override
    public void payment(PaymentInfo paymentInfo) {
        if (log.isDebugEnabled()) {
            log.debug("payment signal received: {}", paymentInfo);
        }
        unprocessedPayments.add(paymentInfo);
    }

    private void processOrderReceived() {
        if (orderState.orderInput().isPresent()) {
            try {
                // validate the order
                OrderValidationResponse validationResponse = orderValidationActivities
                        .validateOrder(orderState.orderInput().get());
                if (log.isDebugEnabled()) {
                    log.debug("order state {}, validationResponse: {}", orderState, validationResponse);
                }

                // perform the next two Activities in parallel (Async)
                // retrieve supplemental data from CAS
                Promise<OrderEntityResponse> orderSupplementalPromise = Async
                        .function(orderSupplementalActivities::getOrderEntity, orderState.customerId(), orderState.orderId());
                // enrich the order by looking up SKUs
                Promise<List<OrderItem>> newItemsPromise = Async.function(orderEnrichmentActivities::enrichOrderItems,
                        orderState);

                // now wait
                OrderEntityResponse orderSupplemental = orderSupplementalPromise.get();
                List<OrderItem> newItems = newItemsPromise.get();

                orderState = orderState.withFullEnrichment(OrderState.OrderStatus.VALID, orderSupplemental.name(),
                        orderSupplemental.company(), orderSupplemental.address(), newItems);

            } catch (ActivityFailure af) {
                if (af.getCause() instanceof ApplicationFailure appFailure) {
                    String type = appFailure.getType(); // e.g. "com.example.MyCustomException"
                    String message = appFailure.getOriginalMessage();
                    if (type.equals(OrderInvalidException.class.getName())) {
                        orderState = orderState.withStatus(OrderState.OrderStatus.INVALID);
                        log.warn("An invalid order has been created {}:{}", orderState.orderInput().get(), message);
                    } else if (type.equals(OrderNotFoundException.class.getName())) {
                        orderState = orderState.withStatus(OrderState.OrderStatus.INVALID);
                        log.warn("The order was valid but now can't be found by CAS {}:{}", orderState.orderInput().get(),
                                message);
                    } else if (type.equals(MissingItemException.class.getName())) {
                        orderState = orderState.withStatus(OrderState.OrderStatus.ITEM_SKU_NOTFOUND);
                        log.warn("An invalid order has been created {}:{}", orderState.orderInput().get(), message);
                    }
                }
            }
        }
    }

    private void processPayment(PaymentInfo paymentInfo) {
        /*
         * Note: if we've already received a valid payment,
         * then it's possible to overwrite if another one arrives that is also valid
         */
        try {
            // validate the payment info with the Payment Service
            PaymentValidationRequest request = new PaymentValidationRequest(
                    paymentInfo.customerId(),
                    paymentInfo.RRN(),
                    orderState.orderTotal().movePointRight(2).longValue(),
                    new Metadata(paymentInfo.orderId()));
            paymentValidationActivities.validate(request);
            orderState = orderState.withValidRRN(paymentInfo.RRN());
        } catch (ActivityFailure af) {
            if (af.getCause() instanceof ApplicationFailure appFailure) {
                String type = appFailure.getType(); // e.g. "com.example.MyCustomException"
                String message = appFailure.getOriginalMessage();
                if (type.equals(PaymentDeclinedException.class.getName())) {
                    orderState = orderState.withStatus(OrderState.OrderStatus.PAYMENT_DECLINED);
                    log.warn("The payment was declined because {}, {}", message, orderState);
                }
            }
        }
    }

    @Override
    public void cancel(String reason) {
        orderCanceled = true;
    }

    @Override
    public void exitWorkflow() { // used for testing purposes
        orderExit = true;
    }

    @Override
    public OrderState get() {
        return orderState;
    }
}
