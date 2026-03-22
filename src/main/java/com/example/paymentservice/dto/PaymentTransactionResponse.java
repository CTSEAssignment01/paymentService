package com.example.paymentservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentTransactionResponse(
        UUID id,
        UUID userId,
        UUID appointmentId,
        String stripeSessionId,
        BigDecimal amount,
        String currency,
        String description,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}