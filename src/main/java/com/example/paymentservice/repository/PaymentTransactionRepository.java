package com.example.paymentservice.repository;

import com.example.paymentservice.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByStripeSessionId(String stripeSessionId);

    java.util.List<PaymentTransaction> findByUserIdAndStatusNotOrderByCreatedAtDesc(UUID userId, String status);
}
