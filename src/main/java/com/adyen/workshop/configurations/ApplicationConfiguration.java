package com.adyen.workshop.configurations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfiguration {
    @Value("${server.port}")
    private int serverPort;

    private static final String BASE_URL = "https://curly-waffle-4j6r4prq7693jjgj-8080.app.github.dev";

    @Value("${ADYEN_API_KEY:#{null}}") // Don't edit @Value(...)
    private String adyenApiKey;

    @Value("${ADYEN_MERCHANT_ACCOUNT:#{null}}") // Don't edit @Value(...)
    private String adyenMerchantAccount;

    @Value("${ADYEN_CLIENT_KEY:#{null}}") // Don't edit @Value(...)
    private String adyenClientKey;

    @Value("${ADYEN_HMAC_KEY:#{null}}") // Don't edit @Value(...)
    private String adyenHmacKey; // We'll cover this in step 16.

    public int getServerPort() {
        return serverPort;
    }

    public String getBaseUrl() {
        return BASE_URL;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getAdyenApiKey() {
        return adyenApiKey;
    }

    public void setAdyenApiKey(String adyenApiKey) {
        this.adyenApiKey = adyenApiKey;
    }

    public String getAdyenMerchantAccount() {
        return adyenMerchantAccount;
    }

    public void setAdyenMerchantAccount(String adyenMerchantAccount) {
        this.adyenMerchantAccount = adyenMerchantAccount;
    }

    public String getAdyenClientKey() {
        return adyenClientKey;
    }

    public void setAdyenClientKey(String adyenClientKey) {
        this.adyenClientKey = adyenClientKey;
    }

    public String getAdyenHmacKey() {
        return adyenHmacKey;
    }

    public void setAdyenHmacKey(String adyenHmacKey) {
        this.adyenHmacKey = adyenHmacKey;
    }
}