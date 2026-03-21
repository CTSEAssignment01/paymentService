package com.example.paymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stripe")
public record StripeProperties(
        String apiKey,
        String webhookSecret,
        String successUrl,
        String cancelUrl,
        String defaultCurrency
) {
}
