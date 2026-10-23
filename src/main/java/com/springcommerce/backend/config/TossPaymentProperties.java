package com.springcommerce.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TossPaymentProperties {

    private final String secretKey;
    private final String baseUrl;

    public TossPaymentProperties(
            @Value("${TOSS_SECRET_KEY:${toss.payments.secret-key:}}")
            String secretKey,

            @Value("${toss.payments.base-url:https://api.tosspayments.com}")
            String baseUrl
    ) {
        this.secretKey = secretKey;
        this.baseUrl = baseUrl;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}