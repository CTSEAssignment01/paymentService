package com.example.paymentservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record InternalCreatePaymentSessionRequest(
        @NotNull UUID appointmentId,
        @NotNull UUID patientId,
        @NotNull @DecimalMin("0.50") BigDecimal amount,
        @NotBlank String description,
        String currency,
        String appointmentDate
) {
}
