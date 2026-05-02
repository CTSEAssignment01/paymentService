package com.example.paymentservice.repository;

import com.example.paymentservice.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    Optional<PaymentTransaction> findByStripeSessionId(String stripeSessionId);

    @Query("select t from PaymentTransaction t where t.userId = :userId and t.status not in ('PENDING', 'CREATED') order by t.createdAt desc")
    java.util.List<PaymentTransaction> findFinalTransactionsByUserIdOrderByCreatedAtDesc(UUID userId);
}
