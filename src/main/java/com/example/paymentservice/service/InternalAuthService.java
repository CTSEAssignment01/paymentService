package com.example.paymentservice.service;

import com.example.paymentservice.config.PaymentSecurityProperties;
import com.example.paymentservice.exception.UnauthorizedException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class InternalAuthService {

    private static final String HEADER_NAME = "X-Internal-Api-Key";

    private final PaymentSecurityProperties paymentSecurityProperties;

    public InternalAuthService(PaymentSecurityProperties paymentSecurityProperties) {
        this.paymentSecurityProperties = paymentSecurityProperties;
    }

    public void verifyInternalApiKey(String providedApiKey) {
        if (!paymentSecurityProperties.internalAuthEnabled()) {
            return;
        }

        String configured = paymentSecurityProperties.internalApiKey();
        if (configured == null || configured.isBlank()) {
            throw new UnauthorizedException("Internal API key is not configured");
        }
        if (providedApiKey == null || providedApiKey.isBlank()) {
            throw new UnauthorizedException("Missing " + HEADER_NAME);
        }

        byte[] providedBytes = providedApiKey.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = configured.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(providedBytes, expectedBytes)) {
            throw new UnauthorizedException("Invalid internal API key");
        }
    }
}
