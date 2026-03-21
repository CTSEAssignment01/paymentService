package com.example.paymentservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProvisionCustomerRequest(
        @NotNull UUID userId,
        @NotBlank @Email String email,
        @NotBlank String name,
        String phone,
        String role
) {
}
