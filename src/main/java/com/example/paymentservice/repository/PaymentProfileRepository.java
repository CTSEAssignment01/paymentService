package com.example.paymentservice.repository;

import com.example.paymentservice.model.PaymentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentProfileRepository extends JpaRepository<PaymentProfile, UUID> {
    Optional<PaymentProfile> findByUserId(UUID userId);
}
