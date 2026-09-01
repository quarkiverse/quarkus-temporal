package io.quarkiverse.temporal.order;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import io.quarkiverse.temporal.order.activities.*;
import io.quarkiverse.temporal.order.model.customer.OrderEntityResponse;
import io.quarkiverse.temporal.order.model.customer.OrderValidationResponse;
import io.quarkiverse.temporal.order.model.customer.PaymentInfo;
import io.quarkiverse.temporal.order.model.order.input.OrderInit;
import io.quarkiverse.temporal.order.model.order.input.OrderInput;
import io.quarkiverse.temporal.order.model.order.input.OrderInputItem;
import io.quarkiverse.temporal.order.model.order.input.OrderInputOrder;
import io.quarkiverse.temporal.order.model.order.payment.PaymentValidationRequest;
import io.quarkiverse.temporal.order.model.order.payment.PaymentValidationResponse;
import io.quarkiverse.temporal.order.model.order.state.OrderItem;
import io.quarkiverse.temporal.order.model.order.state.OrderState;
import io.quarkiverse.temporal.order.model.workflow.OrderEntityConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.worker.WorkerFactory;

@QuarkusTest
public class OrderEntityWorkflowMultiTestFailure1Test {
    static final String custMikeId = "Mike";

    @Inject
    Logger log;

    @ConfigProperty(name = "quarkus.temporal.worker.task-queue")
    String taskQueue;

    @ConfigProperty(name = "quarkus.temporal.namespace")
    String workflowNamespace;

    @ConfigProperty(name = "quarkus.temporal.enable-mock")
    boolean enableMock;

    @ConfigProperty(name = "quarkus.temporal.start-workers")
    boolean startWorkers;

    @Inject
    WorkflowClient workflowClient;

    @Inject
    WorkerFactory workerFactory;

    @Test
    public void testHappyPathOrderProcessing() {
        log.info("starting testHappyPathOrderProcessing...");
        log.info("enableMock: " + enableMock);
        log.info("startWorkers: " + startWorkers);
        // Create test data
        OrderInit orderInit = createOrderInit();
        OrderInput orderInput = createValidOrder();
        PaymentInfo paymentInfo = createPaymentInfo(orderInit);

        // Create mock activities
        LocalConfigActivities mockLocalConfigActivities = mock(LocalConfigActivities.class,
                withSettings().withoutAnnotations());
        OrderValidationActivities mockOrderValidationActivities = mock(OrderValidationActivities.class,
                withSettings().withoutAnnotations());
        PaymentValidationActivities mockPaymentValidationActivities = mock(PaymentValidationActivities.class,
                withSettings().withoutAnnotations());
        OrderSupplementalActivities mockOrderSupplementalActivities = mock(OrderSupplementalActivities.class,
                withSettings().withoutAnnotations());
        OrderEnrichmentActivities mockOrderEnrichmentActivities = mock(OrderEnrichmentActivities.class,
                withSettings().withoutAnnotations());

        // Configure mock behaviors for happy path

        // LocalConfigActivities - return config with short durations for testing
        OrderEntityConfig config = new OrderEntityConfig(
                Duration.ofSeconds(5),
                Duration.ofMillis(100));
        when(mockLocalConfigActivities.getEntityConfig()).thenReturn(config);

        // OrderValidationActivities - return valid order
        when(mockOrderValidationActivities.validateOrder(orderInput))
                .thenReturn(new OrderValidationResponse(true));

        // PaymentValidationActivities - return valid payment
        when(mockPaymentValidationActivities.validate(any(PaymentValidationRequest.class)))
                .thenReturn(new PaymentValidationResponse(true, Optional.empty()));

        // OrderSupplementalActivities - return order entity with name, company, address
        OrderEntityResponse orderEntityResponse = new OrderEntityResponse(
                orderInit.orderId(),
                orderInit.customerId(),
                "John Doe",
                "Test Company",
                "123 Test Street");
        when(mockOrderSupplementalActivities.getOrderEntity(orderInit.customerId(), orderInit.orderId()))
                .thenReturn(orderEntityResponse);

        // OrderEnrichmentActivities - return enriched order items
        List<OrderItem> enrichedItems = new ArrayList<>();
        OrderItem enrichedItem = new OrderItem(
                orderInput.order().items().getFirst().itemId(),
                orderInput.order().items().getFirst().quantity(),
                Optional.of("SKU-12345"),
                Optional.of("BRAND-001"),
                Optional.of(new BigDecimal("29.99")));
        enrichedItems.add(enrichedItem);

        when(mockOrderEnrichmentActivities.enrichOrderItems(any(OrderState.class)))
                .thenReturn(enrichedItems);

        // Register mock activities with the worker
        log.infof("Registering Mock Activities");
        workerFactory.getWorker(taskQueue)
                .registerActivitiesImplementations(
                        mockLocalConfigActivities,
                        mockPaymentValidationActivities,
                        mockOrderEnrichmentActivities,
                        mockOrderValidationActivities,
                        mockOrderSupplementalActivities);

        log.info("Done registering Mock Activities");

        // Create the workflow stub
        log.infof("Creating workflow");
        // Create a typed workflow stub backed by the in-memory test server.<4>
        OrderEntityWorkflow workflow = workflowClient
                .newWorkflowStub(OrderEntityWorkflow.class, WorkflowOptions.newBuilder()
                        .setWorkflowId("%s-%s".formatted(workflowNamespace, UUID.randomUUID().toString()))
                        .setTaskQueue(taskQueue)
                        .build());
        log.info("Done creating workflow");

        // Start the workers (MUST be done AFTER registering activities)
        log.info("Starting workers");
        workerFactory.start();
        log.info("Done starting workers");

        try {
            log.info("Starting workflow with create()");
            // Step 1: Start the workflow with create
            // Start workflow asynchronously
            WorkflowClient.start(workflow::create, orderInit);
            log.info("Workflow started");

            // Step 2: Send order input signal
            log.info("Sending order input signal");
            workflow.orderInput(orderInput);
            log.info("Order input signal sent");

            // Step 3: Send payment signal
            log.info("Sending payment signal");
            workflow.payment(paymentInfo);
            log.info("Payment signal sent");

            // Step 4: Wait for the workflow to complete and verify the result
            log.info("Waiting for workflow to complete");
            String result = WorkflowStub.fromTyped(workflow).getResult(String.class);
            log.infof("Workflow completed with result: %s", result);

            // Verify the result contains expected information
            assertNotNull(result, "Workflow result should not be null");
            assertTrue(result.contains("completed"), "Result should indicate completion");
            assertTrue(result.contains(custMikeId), "Result should contain customer ID");
            assertTrue(result.contains(orderInit.orderId()), "Result should contain order ID");
            assertTrue(result.contains("order canceled false"), "Order should not be canceled");
            assertTrue(result.contains("order expired false"), "Order should not be expired");
            assertTrue(result.contains("order status FULFILLMENT"), "Order status should be FULFILLMENT");
        } finally {
            // Clean up
            workerFactory.shutdown();
        }
    }

    @Test
    public void testMissingPaymentOrderProcessing() {
        log.info("starting testHappyPathOrderProcessing...");
        log.info("enableMock: " + enableMock);
        log.info("startWorkers: " + startWorkers);
        // Create test data
        OrderInit orderInit = createOrderInit();
        OrderInput orderInput = createValidOrder();

        // Create mock activities
        LocalConfigActivities mockLocalConfigActivities = mock(LocalConfigActivities.class,
                withSettings().withoutAnnotations());
        OrderValidationActivities mockOrderValidationActivities = mock(OrderValidationActivities.class,
                withSettings().withoutAnnotations());
        PaymentValidationActivities mockPaymentValidationActivities = mock(PaymentValidationActivities.class,
                withSettings().withoutAnnotations());
        OrderSupplementalActivities mockOrderSupplementalActivities = mock(OrderSupplementalActivities.class,
                withSettings().withoutAnnotations());
        OrderEnrichmentActivities mockOrderEnrichmentActivities = mock(OrderEnrichmentActivities.class,
                withSettings().withoutAnnotations());

        // Configure mock behaviors for happy path

        // LocalConfigActivities - return config with short durations for testing
        OrderEntityConfig config = new OrderEntityConfig(
                Duration.ofSeconds(5),
                Duration.ofMillis(100));
        when(mockLocalConfigActivities.getEntityConfig()).thenReturn(config);

        // OrderValidationActivities - return valid order
        when(mockOrderValidationActivities.validateOrder(orderInput))
                .thenReturn(new OrderValidationResponse(true));

        // PaymentValidationActivities - return valid payment
        when(mockPaymentValidationActivities.validate(any(PaymentValidationRequest.class)))
                .thenReturn(new PaymentValidationResponse(true, Optional.empty()));

        // OrderSupplementalActivities - return order entity with name, company, address
        OrderEntityResponse orderEntityResponse = new OrderEntityResponse(
                orderInit.orderId(),
                orderInit.customerId(),
                "John Doe",
                "Test Company",
                "123 Test Street");
        when(mockOrderSupplementalActivities.getOrderEntity(orderInit.customerId(), orderInit.orderId()))
                .thenReturn(orderEntityResponse);

        // OrderEnrichmentActivities - return enriched order items
        List<OrderItem> enrichedItems = new ArrayList<>();
        OrderItem enrichedItem = new OrderItem(
                orderInput.order().items().getFirst().itemId(),
                orderInput.order().items().getFirst().quantity(),
                Optional.of("SKU-12345"),
                Optional.of("BRAND-001"),
                Optional.of(new BigDecimal("29.99")));
        enrichedItems.add(enrichedItem);

        when(mockOrderEnrichmentActivities.enrichOrderItems(any(OrderState.class)))
                .thenReturn(enrichedItems);

        // Register mock activities with the worker
        log.infof("Registering Mock Activities");
        workerFactory.getWorker(taskQueue)
                .registerActivitiesImplementations(
                        mockLocalConfigActivities,
                        mockPaymentValidationActivities,
                        mockOrderEnrichmentActivities,
                        mockOrderValidationActivities,
                        mockOrderSupplementalActivities);

        log.info("Done registering Mock Activities");

        // Create the workflow stub
        log.infof("Creating workflow");
        // Create a typed workflow stub backed by the in-memory test server.<4>
        OrderEntityWorkflow workflow = workflowClient
                .newWorkflowStub(OrderEntityWorkflow.class, WorkflowOptions.newBuilder()
                        .setWorkflowId("%s-%s".formatted(workflowNamespace, UUID.randomUUID().toString()))
                        .setTaskQueue(taskQueue)
                        .build());
        log.info("Done creating workflow");

        // Start the workers (MUST be done AFTER registering activities)
        log.info("Starting workers");
        workerFactory.start();
        log.info("Done starting workers");

        try {
            log.info("Starting workflow with create()");
            // Step 1: Start the workflow with create
            // Start workflow asynchronously
            WorkflowClient.start(workflow::create, orderInit);
            log.info("Workflow started");

            // Step 2: Send order input signal
            log.info("Sending order input signal");
            workflow.orderInput(orderInput);
            log.info("Order input signal sent");

            // Step 3: Wait for the workflow to complete and verify the result
            log.info("Waiting for workflow to complete");
            String result = WorkflowStub.fromTyped(workflow).getResult(String.class);
            log.infof("Workflow completed with result: %s", result);

            // Verify the result contains expected information
            // Verify the result contains expected information
            assertNotNull(result, "Workflow result should not be null");
            assertTrue(result.contains("completed"), "Result should indicate completion");
            assertTrue(result.contains(custMikeId), "Result should contain customer ID");
            assertTrue(result.contains(orderInit.orderId()), "Result should contain order ID");
            assertTrue(result.contains("order canceled false"), "Order should not be canceled");
            assertTrue(result.contains("order expired true"), "Order should not be expired");
            assertTrue(result.contains("order status EXPIRED"), "Order status should be EXPIRED");
        } finally {
            // Clean up
            workerFactory.shutdown();
        }
    }

    private OrderInit createOrderInit() {
        return new OrderInit(
                UUID.randomUUID().toString(),
                custMikeId,
                "xyzzy");
    }

    private OrderInput createValidOrder() {
        OrderInputItem item = new OrderInputItem("baseball", 1);
        ArrayList<OrderInputItem> items = new ArrayList<>();
        items.add(item);

        return new OrderInput(
                UUID.randomUUID().toString(),
                custMikeId,
                new OrderInputOrder(
                        "xyzzy",
                        items));
    }

    private PaymentInfo createPaymentInfo(OrderInit orderInit) {
        return new PaymentInfo(
                UUID.randomUUID().toString(),
                orderInit.customerId(),
                orderInit.orderId(),
                "RRN-123456789");
    }
}
