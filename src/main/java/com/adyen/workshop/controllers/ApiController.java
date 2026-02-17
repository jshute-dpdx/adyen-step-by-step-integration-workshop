package com.adyen.workshop.controllers;

import com.adyen.model.RequestOptions;
import com.adyen.model.checkout.*;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import com.adyen.workshop.service.SubscriptionService;
import com.adyen.service.checkout.ModificationsApi;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for using the Adyen payments API.
 */
@RestController
public class ApiController {
    private final Logger log = LoggerFactory.getLogger(ApiController.class);

    private static final String DEFAULT_SHOPPER_REFERENCE = "shopperReference";

    private static final long DEFAULT_PREAUTH_CENTS = 1000L;   // 10.00 EUR
    private static final long DEFAULT_MODIFY_CENTS = 6600L;    // 66.00 EUR

    private final ApplicationConfiguration applicationConfiguration;
    private final PaymentsApi paymentsApi;
    private final ModificationsApi modificationsApi;
    private final SubscriptionService subscriptionService;

    public ApiController(ApplicationConfiguration applicationConfiguration, PaymentsApi paymentsApi, ModificationsApi modificationsApi, SubscriptionService subscriptionService) {
        this.applicationConfiguration = applicationConfiguration;
        this.paymentsApi = paymentsApi;
        this.modificationsApi = modificationsApi;
        this.subscriptionService = subscriptionService;
    }

    // Step 0
    @GetMapping("/hello-world")
    public ResponseEntity<String> helloWorld() throws Exception {
        return ResponseEntity.ok().body("This is the 'Hello World' from the workshop - You've successfully finished step 0!");
    }

    // Step 7
    @PostMapping("/api/paymentMethods")
    public ResponseEntity<PaymentMethodsResponse> paymentMethods() throws IOException, ApiException {
        var paymentMethodsRequest = new PaymentMethodsRequest();
        paymentMethodsRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());

        log.info("Retrieving available Payment Methods from Adyen {}", paymentMethodsRequest);
        var response = paymentsApi.paymentMethods(paymentMethodsRequest);
        log.info("Payment Methods response from Adyen {}", response);
        return ResponseEntity.ok().body(response);
    }

    // Step 9 - Implement the /payments call to Adyen.
    @PostMapping("/api/payments")
    public ResponseEntity<PaymentResponse> payments(@RequestBody PaymentRequest body) throws IOException, ApiException {
        var paymentRequest = new PaymentRequest();
        
        var amount = new Amount()
                .currency("EUR")
                .value(9998L);
        paymentRequest.setAmount(amount);
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);
    
        paymentRequest.setPaymentMethod(body.getPaymentMethod());
    
        var orderRef = UUID.randomUUID().toString();
        paymentRequest.setReference(orderRef);
        paymentRequest.setReturnUrl(applicationConfiguration.getBaseUrl() + "/handleShopperRedirect");


        // Step 12 3DS2 Redirect - Add the following additional parameters to your existing payment request for 3DS2 Redirect:
        // Note: Visa requires additional properties to be sent in the request, see documentation for Redirect 3DS2: https://docs.adyen.com/online-payments/3d-secure/redirect-3ds2/web-drop-in/#make-a-payment
        var authenticationData = new AuthenticationData();
        authenticationData.setAttemptAuthentication(AuthenticationData.AttemptAuthenticationEnum.ALWAYS);
        paymentRequest.setAuthenticationData(authenticationData);

        // Change the following lines, if you want to enable the Native 3DS2 flow:
        // Note: Visa requires additional properties to be sent in the request, see documentation for Native 3DS2: https://docs.adyen.com/online-payments/3d-secure/native-3ds2/web-drop-in/#make-a-payment
        //authenticationData.setThreeDSRequestData(new ThreeDSRequestData().nativeThreeDS(ThreeDSRequestData.NativeThreeDSEnum.PREFERRED));
        //paymentRequest.setAuthenticationData(authenticationData);

        paymentRequest.setOrigin(applicationConfiguration.getBaseUrl());
        paymentRequest.setBrowserInfo(body.getBrowserInfo());
        paymentRequest.setShopperIP("192.168.0.1");
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE);

        var billingAddress = new BillingAddress();
        billingAddress.setCity("Amsterdam");
        billingAddress.setCountry("NL");
        billingAddress.setPostalCode("1012KK");
        billingAddress.setStreet("Rokin");
        billingAddress.setHouseNumberOrName("49");
        paymentRequest.setBillingAddress(billingAddress);

        // Step 19 (optional) - shopperEmail, shopperReference, lineItems is required for klarna
        LineItem lineItem1 = new LineItem()
                .quantity(1L)
                .taxPercentage(2100L)
                .amountIncludingTax(4999L)
                .imageUrl("https://adyen.com")
                .description("The best sunglasses")
                .id("uniqueId-1")
                .productUrl("https://adyen.com");

        LineItem lineItem2 = new LineItem()
                .quantity(1L)
                .taxPercentage(2100L)
                .amountIncludingTax(4999L)
                .imageUrl("https://adyen.com")
                .description("The best headphones")
                .id("uniqueId-2")
                .productUrl("https://adyen.com");

        paymentRequest.setCountryCode("NL");
        paymentRequest.setShopperReference("shopperReference");
        paymentRequest.setShopperEmail("example@email.com");
        paymentRequest.setLineItems(Arrays.asList(lineItem1, lineItem2));
        
        // Step 11 - Optionally add the idempotency key
        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());
    
        log.info("PaymentsRequest {}", paymentRequest);
        var response = paymentsApi.payments(paymentRequest, requestOptions); // add RequestOptions here
        log.info("PaymentsResponse {}", response);
        
        return ResponseEntity.ok().body(response);
    }

    /**
     * Pre-authorize a payment (reserve funds without capturing).
     * Amount: use request body amount if set, else query param amount (minor units), else default 10 EUR.
     * @see <a href="https://docs.adyen.com/online-payments/adjust-authorisation/adjust-with-preauth/#pre-authorize">Adjust an authorization</a>
     */
    @PostMapping("/api/preauthorisation")
    public ResponseEntity<PaymentResponse> preauthorisation(
            @RequestBody PaymentRequest body,
            @RequestParam(required = false) Long amount) throws IOException, ApiException {
        long amountMinor = DEFAULT_PREAUTH_CENTS;
        if (body.getAmount() != null && body.getAmount().getValue() != null) {
            amountMinor = body.getAmount().getValue();
        } else if (amount != null) {
            amountMinor = amount;
        }
        String currency = (body.getAmount() != null && body.getAmount().getCurrency() != null) ? body.getAmount().getCurrency() : "EUR";
        var paymentRequest = new PaymentRequest();
        paymentRequest.setAmount(new Amount().currency(currency).value(amountMinor));
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);
        paymentRequest.setPaymentMethod(body.getPaymentMethod());

        var orderRef = UUID.randomUUID().toString();
        paymentRequest.setReference(orderRef);
        paymentRequest.setReturnUrl(applicationConfiguration.getBaseUrl() + "/handleShopperRedirect");

        var authenticationData = new AuthenticationData();
        authenticationData.setAttemptAuthentication(AuthenticationData.AttemptAuthenticationEnum.ALWAYS);
        paymentRequest.setAuthenticationData(authenticationData);

        paymentRequest.putAdditionalDataItem("authorisationType", "PreAuth");
        paymentRequest.putAdditionalDataItem("manualCapture", "true");

        paymentRequest.setOrigin(applicationConfiguration.getBaseUrl());
        paymentRequest.setBrowserInfo(body.getBrowserInfo());
        paymentRequest.setShopperIP("192.168.0.1");
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE);

        var billingAddress = new BillingAddress();
        billingAddress.setCity("Amsterdam");
        billingAddress.setCountry("NL");
        billingAddress.setPostalCode("1012KK");
        billingAddress.setStreet("Rokin");
        billingAddress.setHouseNumberOrName("49");
        paymentRequest.setBillingAddress(billingAddress);

        paymentRequest.setCountryCode("NL");
        paymentRequest.setShopperReference(DEFAULT_SHOPPER_REFERENCE);
        paymentRequest.setShopperEmail("example@email.com");

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Preauthorisation request {}", paymentRequest);
        var response = paymentsApi.payments(paymentRequest, requestOptions);
        log.info("Preauthorisation response pspReference={}", response != null ? response.getPspReference() : null);
        return ResponseEntity.ok().body(response);
    }

    /**
     * Zero-auth payment to tokenize a card for subscriptions.
     * Uses storePaymentMethod and recurringProcessingModel.
     * Recurring processing models: Subscription (fixed schedule), CardOnFile (one-click/omnichannel),
     * UnscheduledCardOnFile (variable amount, non-fixed schedule). This endpoint uses Subscription.
     * The recurringDetailReference / storedPaymentMethodId is received via RECURRING_CONTRACT or recurring.token.created webhook.
     */
    @PostMapping("/api/subscription-create")
    public ResponseEntity<?> subscriptionCreate(@RequestBody PaymentRequest body) throws IOException, ApiException {
        var paymentRequest = new PaymentRequest();
        paymentRequest.setAmount(new Amount().currency("EUR").value(0L));
        paymentRequest.setMerchantAccount(applicationConfiguration.getAdyenMerchantAccount());
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB);
        paymentRequest.setPaymentMethod(body.getPaymentMethod());
        var orderRef = UUID.randomUUID().toString();
        paymentRequest.setReference(orderRef);
        paymentRequest.setReturnUrl(applicationConfiguration.getBaseUrl() + "/handleShopperRedirect");

        var authenticationData = new AuthenticationData();
        authenticationData.setAttemptAuthentication(AuthenticationData.AttemptAuthenticationEnum.ALWAYS);
        paymentRequest.setAuthenticationData(authenticationData);

        paymentRequest.setOrigin(applicationConfiguration.getBaseUrl());
        paymentRequest.setBrowserInfo(body.getBrowserInfo());
        paymentRequest.setShopperIP("192.168.0.1");
        paymentRequest.setShopperInteraction(PaymentRequest.ShopperInteractionEnum.ECOMMERCE);

        paymentRequest.setStorePaymentMethod(true);
        paymentRequest.setRecurringProcessingModel(PaymentRequest.RecurringProcessingModelEnum.SUBSCRIPTION);

        String shopperRef = body.getShopperReference() != null ? body.getShopperReference() : DEFAULT_SHOPPER_REFERENCE;
        paymentRequest.setShopperReference(shopperRef);
        paymentRequest.setCountryCode("NL");
        paymentRequest.setShopperEmail(body.getShopperEmail() != null ? body.getShopperEmail() : "example@email.com");

        var billingAddress = new BillingAddress();
        billingAddress.setCity("Amsterdam");
        billingAddress.setCountry("NL");
        billingAddress.setPostalCode("1012KK");
        billingAddress.setStreet("Rokin");
        billingAddress.setHouseNumberOrName("49");
        paymentRequest.setBillingAddress(billingAddress);

        var requestOptions = new RequestOptions();
        requestOptions.setIdempotencyKey(UUID.randomUUID().toString());

        log.info("Subscription-create (zero-auth) request {}", paymentRequest);
        var response = paymentsApi.payments(paymentRequest, requestOptions);
        log.info("Subscription-create response {}", response);
        return ResponseEntity.ok().body(response);
    }

    /**
     * Make a payment with a token (recurringDetailReference from RECURRING_CONTRACT webhook).
     * For testing: copy/paste the token into the URL.
     */
    @GetMapping("/makepaymentwithtoken/{token}")
    public ResponseEntity<?> makePaymentWithToken(
            @PathVariable String token,
            @RequestParam(required = false) Long amount) throws IOException, ApiException {
        var response = subscriptionService.chargeWithToken(token, amount);
        return ResponseEntity.ok().body(response);
    }

    /**
     * Adjust a pre-authorisation amount.
     * Pass the token (pspReference from AUTHORISATION). Optional query param: amount. Default 66 EUR.
     */
    @GetMapping("/api/modify-amount/{token}")
    public ResponseEntity<?> modifyAmount(
            @PathVariable String token,
            @RequestParam(required = false) Long amount) throws IOException, ApiException {
        long amountMinor = amount != null ? amount : DEFAULT_MODIFY_CENTS;
        var request = new PaymentAmountUpdateRequest()
                .amount(new Amount().currency("EUR").value(amountMinor))
                .merchantAccount(applicationConfiguration.getAdyenMerchantAccount())
                .reference(UUID.randomUUID().toString());
        log.info("Modify amount request for token, amount={} cents", amountMinor);
        var response = modificationsApi.updateAuthorisedAmount(token, request);
        log.info("Modify amount response: status={}", response != null ? response.getStatus() : null);
        return ResponseEntity.ok().body(response);
    }

    /**
     * Capture by token in path. Optional query param: amount. Default 66 EUR.
     */
    @GetMapping("/api/capture/{token}")
    public ResponseEntity<?> captureGet(
            @PathVariable String token,
            @RequestParam(required = false) Long amount) throws IOException, ApiException {
        long amountMinor = amount != null ? amount : DEFAULT_MODIFY_CENTS;
        var request = new PaymentCaptureRequest()
                .amount(new Amount().currency("EUR").value(amountMinor))
                .merchantAccount(applicationConfiguration.getAdyenMerchantAccount())
                .reference(UUID.randomUUID().toString());
        log.info("Capture request for token, amount={} cents", amountMinor);
        var response = modificationsApi.captureAuthorisedPayment(token, request);
        log.info("Capture response: status={}", response != null ? response.getStatus() : null);
        return ResponseEntity.ok().body(response);
    }

    /**
     * Refund by token (capture pspReference) in path. Optional query param: amount (minor units). Default 66 EUR.
     */
    @GetMapping("/api/refund/{token}")
    public ResponseEntity<?> refundGet(
            @PathVariable String token,
            @RequestParam(required = false) Long amount) throws IOException, ApiException {
        long amountMinor = amount != null ? amount : DEFAULT_MODIFY_CENTS;
        var request = new PaymentRefundRequest()
                .amount(new Amount().currency("EUR").value(amountMinor))
                .merchantAccount(applicationConfiguration.getAdyenMerchantAccount())
                .reference(UUID.randomUUID().toString());
        log.info("Refund request for token, amount={} cents", amountMinor);
        var response = modificationsApi.refundCapturedPayment(token, request);
        log.info("Refund response: status={}", response != null ? response.getStatus() : null);
        return ResponseEntity.ok().body(response);
    }

    // Step 13 - Handle details call (triggered after Native 3DS2 flow)
    @PostMapping("/api/payments/details")
    public ResponseEntity<PaymentDetailsResponse> paymentsDetails(@RequestBody PaymentDetailsRequest detailsRequest) throws IOException, ApiException
    {

        return ResponseEntity.ok().body(null);
    }

    // Step 14 - Handle Redirect 3DS2 during payment.
    @GetMapping("/handleShopperRedirect")
    public RedirectView redirect(@RequestParam(required = false) String payload, @RequestParam(required = false) String redirectResult) throws IOException, ApiException {
        var paymentDetailsRequest = new PaymentDetailsRequest();

        PaymentCompletionDetails paymentCompletionDetails = new PaymentCompletionDetails();

        // Handle redirect result or payload
        if (redirectResult != null && !redirectResult.isEmpty()) {
            // For redirect, you are redirected to an Adyen domain to complete the 3DS2 challenge
            // After completing the 3DS2 challenge, you get the redirect result from Adyen in the returnUrl
            // We then pass on the redirectResult
            paymentCompletionDetails.redirectResult(redirectResult);
        } else if (payload != null && !payload.isEmpty()) {
            paymentCompletionDetails.payload(payload);
        }

        paymentDetailsRequest.setDetails(paymentCompletionDetails);

        var paymentsDetailsResponse = paymentsApi.paymentsDetails(paymentDetailsRequest);
        log.info("PaymentsDetailsResponse {}", paymentsDetailsResponse);

        // Handle response and redirect user accordingly
        var redirectURL = applicationConfiguration.getBaseUrl() + "/result/";
        switch (paymentsDetailsResponse.getResultCode()) {
            case AUTHORISED:
                redirectURL += "success";
                break;
            case PENDING:
            case RECEIVED:
                redirectURL += "pending";
                break;
            case REFUSED:
                redirectURL += "failed";
                break;
            default:
                redirectURL += "error";
                break;
        }
        return new RedirectView(redirectURL + "?reason=" + paymentsDetailsResponse.getResultCode());
    }
}
