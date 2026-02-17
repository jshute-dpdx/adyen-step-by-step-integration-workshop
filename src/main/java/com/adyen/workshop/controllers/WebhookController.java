package com.adyen.workshop.controllers;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.util.HMACValidator;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import org.apache.coyote.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.SignatureException;
import java.util.Map;

/**
 * REST controller for receiving Adyen webhook notifications
 */
@RestController
public class WebhookController {
    private final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private static final String EVENT_RECURRING_CONTRACT = "RECURRING_CONTRACT";
    private static final String EVENT_AUTHORISATION = "AUTHORISATION";
    private static final String EVENT_CAPTURE = "CAPTURE";
    private static final String EVENT_CAPTURE_FAILED = "CAPTURE_FAILED";
    private static final String EVENT_REFUND = "REFUND";
    private static final String EVENT_REFUND_FAILED = "REFUND_FAILED";
    private static final String EVENT_REFUNDED_REVERSED = "REFUNDED_REVERSED";
    private static final String ADDITIONAL_DATA_RECURRING_DETAIL_REF = "recurring.recurringDetailReference";

    private final ApplicationConfiguration applicationConfiguration;
    private final HMACValidator hmacValidator;

    @Autowired
    public WebhookController(ApplicationConfiguration applicationConfiguration, HMACValidator hmacValidator) {
        this.applicationConfiguration = applicationConfiguration;
        this.hmacValidator = hmacValidator;
    }

    @PostMapping("/webhooks")
    public ResponseEntity<String> webhooks(@RequestBody String json) throws Exception {
        log.info("Received: {}", json);
        var notificationRequest = NotificationRequest.fromJson(json);
        var notificationRequestItem = notificationRequest.getNotificationItems().stream().findFirst();

        try {
            NotificationRequestItem item = notificationRequestItem.get();

            // Step 16 - Validate the HMAC signature using the ADYEN_HMAC_KEY
            if (!hmacValidator.validateHMAC(item, this.applicationConfiguration.getAdyenHmacKey())) {
                log.warn("Could not validate HMAC signature for incoming webhook message: {}", item);
                return ResponseEntity.unprocessableEntity().build();
            }

            // Success, log and handle subscription tokenization
            log.info("Received webhook with eventCode {} {}", item.getEventCode(), item.toString());

            switch (item.getEventCode()) {
                case EVENT_RECURRING_CONTRACT -> handleRecurringContract(item);
                case EVENT_AUTHORISATION -> handleAuthorisation(item);
                case EVENT_CAPTURE -> handleCapture(item);
                case EVENT_CAPTURE_FAILED -> handleCaptureFailed(item);
                case EVENT_REFUND -> handleRefund(item);
                case EVENT_REFUND_FAILED -> handleRefundFailed(item);
                case EVENT_REFUNDED_REVERSED -> handleRefundedReversed(item);
                default -> { }
            }

            return ResponseEntity.accepted().build();
        } catch (SignatureException e) {
            // Handle invalid signature
            return ResponseEntity.unprocessableEntity().build();
        } catch (Exception e) {
            // Handle all other errors
            return ResponseEntity.status(500).build();
        }
    }

    private void handleRecurringContract(NotificationRequestItem item) {
        var additionalData = item.getAdditionalData();
        if (additionalData != null) {
            String ref = additionalData.get(ADDITIONAL_DATA_RECURRING_DETAIL_REF);
            if (ref != null) {
                log.info("RECURRING_CONTRACT token (copy for /makepaymentwithtoken/{{}}): {}", ref, ref);
            }
        }
    }

    private void handleAuthorisation(NotificationRequestItem item) {
        log.info("AUTHORISATION: success={}, pspReference={}", item.isSuccess(), item.getPspReference());
    }

    private void handleCapture(NotificationRequestItem item) {
        log.info("CAPTURE: success={}, pspReference={}, originalReference={}", item.isSuccess(), item.getPspReference(), item.getOriginalReference());
    }

    private void handleCaptureFailed(NotificationRequestItem item) {
        log.warn("CAPTURE_FAILED: pspReference={}, originalReference={}, reason={}", item.getPspReference(), item.getOriginalReference(), item.getReason());
    }

    private void handleRefund(NotificationRequestItem item) {
        log.info("REFUND: success={}, pspReference={}, originalReference={}", item.isSuccess(), item.getPspReference(), item.getOriginalReference());
    }

    private void handleRefundFailed(NotificationRequestItem item) {
        log.warn("REFUND_FAILED: pspReference={}, originalReference={}, reason={}", item.getPspReference(), item.getOriginalReference(), item.getReason());
    }

    private void handleRefundedReversed(NotificationRequestItem item) {
        log.info("REFUNDED_REVERSED: pspReference={}, originalReference={}", item.getPspReference(), item.getOriginalReference());
    }
}