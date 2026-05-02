package com.example.paymentservice.repository;

import com.example.paymentservice.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;
import java.util.List;


public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByStripeSessionId(String stripeSessionId);

    List<PaymentTransaction> findFinalTransactionsByUserIdOrderByCreatedAtDesc(UUID userId, String status);
}
