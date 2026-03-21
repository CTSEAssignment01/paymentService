package com.example.paymentservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentProfileResponse(
        UUID userId,
        String email,
        String name,
        String phone,
        String stripeCustomerId,
        boolean created,
        LocalDateTime createdAt
) {
}
