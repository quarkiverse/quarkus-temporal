package io.quarkiverse.temporal.order.activities;

import io.quarkiverse.temporal.order.model.order.payment.PaymentValidationRequest;
import io.quarkiverse.temporal.order.model.order.payment.PaymentValidationResponse;
import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "PaymentValidation")
public interface PaymentValidationActivities {

    PaymentValidationResponse validate(PaymentValidationRequest request);
}
