package com.example.paymentservice.dto;

public record CreateCheckoutSessionResponse(
        String sessionId,
        String url,
        String status
) {
}
