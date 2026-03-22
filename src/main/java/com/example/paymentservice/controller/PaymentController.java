package com.example.paymentservice.controller;

import com.example.paymentservice.dto.CreateCheckoutSessionRequest;
import com.example.paymentservice.dto.CreateCheckoutSessionResponse;
import com.example.paymentservice.dto.PaymentTransactionResponse;
import com.example.paymentservice.dto.PaymentProfileResponse;
import com.example.paymentservice.service.CheckoutService;
import com.example.paymentservice.service.PaymentProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentProfileService paymentProfileService;
    private final CheckoutService checkoutService;

    public PaymentController(PaymentProfileService paymentProfileService, CheckoutService checkoutService) {
        this.paymentProfileService = paymentProfileService;
        this.checkoutService = checkoutService;
    }

    @GetMapping("/customers/{userId}")
    public ResponseEntity<PaymentProfileResponse> getByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(paymentProfileService.getByUserId(userId));
    }

    @PostMapping("/checkout-session")
    public ResponseEntity<CreateCheckoutSessionResponse> createCheckoutSession(
            @Valid @RequestBody CreateCheckoutSessionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(checkoutService.createCheckoutSession(request));
    }

    @GetMapping("/users/{userId}/transactions")
    public ResponseEntity<List<PaymentTransactionResponse>> getUserTransactions(@PathVariable UUID userId) {
        return ResponseEntity.ok(checkoutService.getTransactionsForUser(userId));
    }

    @PostMapping("/checkout-session/{sessionId}/confirm")
    public ResponseEntity<PaymentTransactionResponse> confirmCheckoutSession(@PathVariable String sessionId) {
        return ResponseEntity.ok(checkoutService.confirmCheckoutSession(sessionId));
    }

    @PostMapping("/webhooks/stripe")
    public ResponseEntity<Void> receiveStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature
    ) {
        checkoutService.handleStripeWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
