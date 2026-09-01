package io.quarkiverse.temporal.order;

import io.quarkiverse.temporal.order.model.customer.PaymentInfo;
import io.quarkiverse.temporal.order.model.order.input.OrderInit;
import io.quarkiverse.temporal.order.model.order.input.OrderInput;
import io.quarkiverse.temporal.order.model.order.state.OrderState;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface OrderEntityWorkflow {
    @WorkflowMethod
    String create(OrderInit orderInit);

    @SignalMethod
    void orderInput(OrderInput orderInput);

    @SignalMethod
    void payment(PaymentInfo paymentInfo);

    @SignalMethod
    void cancel(String reason);

    @SignalMethod
    void exitWorkflow();

    @QueryMethod
    OrderState get();
}
