package com.adyen.workshop.service;

import com.adyen.model.RequestOptions;
import com.adyen.model.checkout.Amount;
import com.adyen.model.checkout.CheckoutPaymentMethod;
import com.adyen.model.checkout.PaymentRequest;
import com.adyen.model.checkout.PaymentResponse;
import com.adyen.model.checkout.StoredPaymentMethodDetails;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.UUID;

/**
 * Service for charging using a subscription token (recurringDetailReference / storedPaymentMethodId).
 */
@Service
public class SubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private static final String DEFAULT_SHOPPER_REFERENCE = "shopperReference";
    private static final long DEFAULT_AMOUNT_CENTS = 500L;

    private final ApplicationConfiguration applicationConfiguration;
    private final PaymentsApi paymentsApi;

    public SubscriptionService(ApplicationConfiguration applicationConfiguration, PaymentsApi paymentsApi) {
        this.applicationConfiguration = applicationConfiguration;
        this.paymentsApi = paymentsApi;
    }

    /**
     * Charge once using the given token (recurringDetailReference from RECURRING_CONTRACT webhook).
     * @param amountMinorUnits amount in minor units (e.g. 500 = 5.00 EUR). If null, uses default.
     */
    public PaymentResponse chargeWithToken(String token, Long amountMinorUnits) throws IOException, ApiException {
        long amount = amountMinorUnits != null ? amountMinorUnits : DEFAULT_AMOUNT_CENTS;
        var storedDetails = new StoredPaymentMethodDetails().storedPaymentMethodId(token);
        var paymentMethod = new CheckoutPaymentMethod(storedDetails);

        var paymentRequest = new PaymentRequest();
        paymentRequest.setAmount(new Amount().currency("EUR").value(amount));
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);
        paymentRequest.setPaymentMethod(paymentMethod);
        paymentRequest.setReference(UUID.randomUUID().toString());
        paymentRequest.setReturnUrl("http://localhost:8080/handleShopperRedirect");
        paymentRequest.setShopperReference(DEFAULT_SHOPPER_REFERENCE);
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.CONTAUTH);
        paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);
        paymentRequest.setCountryCode("NL");

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Subscription charge with token");
        return paymentsApi.payments(paymentRequest, requestOptions);
    }
}
