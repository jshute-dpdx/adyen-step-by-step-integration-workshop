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
 * Service for charging a shopper using their stored subscription token.
 */
@Service
public class SubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private static final String DEFAULT_SHOPPER_REFERENCE = "shopperReference";
    private static final long SUBSCRIPTION_AMOUNT_CENTS = 500L;

    private final ApplicationConfiguration applicationConfiguration;
    private final PaymentsApi paymentsApi;
    private final SubscriptionTokenStore subscriptionTokenStore;

    public SubscriptionService(ApplicationConfiguration applicationConfiguration, PaymentsApi paymentsApi, SubscriptionTokenStore subscriptionTokenStore) {
        this.applicationConfiguration = applicationConfiguration;
        this.paymentsApi = paymentsApi;
        this.subscriptionTokenStore = subscriptionTokenStore;
    }

    /**
     * Charge the shopper once using their stored token. Returns the payment response or null if no token.
     */
    public PaymentResponse chargeSubscription(String shopperReference) throws IOException, ApiException {
        String ref = shopperReference != null ? shopperReference : DEFAULT_SHOPPER_REFERENCE;
        String token = subscriptionTokenStore.getToken(ref);
        if (token == null) {
            log.warn("No stored token for shopperReference {}", ref);
            return null;
        }

        var storedDetails = new StoredPaymentMethodDetails().storedPaymentMethodId(token);
        var paymentMethod = new CheckoutPaymentMethod(storedDetails);

        var paymentRequest = new PaymentRequest();
        paymentRequest.setAmount(new Amount().currency("EUR").value(SUBSCRIPTION_AMOUNT_CENTS));
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);
        paymentRequest.setPaymentMethod(paymentMethod);
        paymentRequest.setReference(UUID.randomUUID().toString());
        paymentRequest.setReturnUrl("http://localhost:8080/handleShopperRedirect");
        paymentRequest.setShopperReference(ref);
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.CONTAUTH);
        paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);
        paymentRequest.setCountryCode("NL");

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Subscription charge for shopper {}", ref);
        return paymentsApi.payments(paymentRequest, requestOptions);
    }
}
