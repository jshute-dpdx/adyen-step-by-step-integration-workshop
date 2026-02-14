package com.adyen.workshop.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for subscription tokens (recurringDetailReference / storedPaymentMethodId).
 * Keyed by shopperReference. For production, use a database.
 */
@Component
public class SubscriptionTokenStore {
    private final Map<String, String> shopperToToken = new ConcurrentHashMap<>();

    public void storeToken(String shopperReference, String token) {
        if (shopperReference != null && token != null) {
            shopperToToken.put(shopperReference, token);
        }
    }

    public String getToken(String shopperReference) {
        return shopperReference != null ? shopperToToken.get(shopperReference) : null;
    }

    public String removeToken(String shopperReference) {
        return shopperReference != null ? shopperToToken.remove(shopperReference) : null;
    }

    public boolean hasToken(String shopperReference) {
        return getToken(shopperReference) != null;
    }
}
