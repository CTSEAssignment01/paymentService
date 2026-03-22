package com.example.paymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.security")
public record PaymentSecurityProperties(String internalApiKey, boolean internalAuthEnabled) {
}
