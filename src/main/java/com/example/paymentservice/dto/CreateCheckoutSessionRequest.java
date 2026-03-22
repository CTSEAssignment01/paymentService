package com.example.paymentservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCheckoutSessionRequest(
        @NotNull UUID userId,
        @NotNull @DecimalMin("0.50") BigDecimal amount,
        @NotBlank String description,
        String currency,
        UUID appointmentId
) {
}
