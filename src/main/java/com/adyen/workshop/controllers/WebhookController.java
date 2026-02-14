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

            if (EVENT_RECURRING_CONTRACT.equals(item.getEventCode())) {
                var additionalData = item.getAdditionalData();
                if (additionalData != null) {
                    String ref = additionalData.get(ADDITIONAL_DATA_RECURRING_DETAIL_REF);
                    if (ref != null) {
                        log.info("RECURRING_CONTRACT token (copy for /makepaymentwithtoken/{{}}): {}", ref, ref);
                    }
                }
            } else if (EVENT_AUTHORISATION.equals(item.getEventCode())) {
                log.info("AUTHORISATION webhook: success={}, pspReference={}, merchantReference={}", item.isSuccess(), item.getPspReference(), item.getMerchantReference());
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
}