package com.example.paymentservice.controller;

import com.example.paymentservice.dto.PaymentProfileResponse;
import com.example.paymentservice.dto.ProvisionCustomerRequest;
import com.example.paymentservice.dto.CreateCheckoutSessionRequest;
import com.example.paymentservice.dto.CreateCheckoutSessionResponse;
import com.example.paymentservice.dto.InternalCreatePaymentSessionRequest;
import com.example.paymentservice.exception.ResourceNotFoundException;
import com.example.paymentservice.service.CheckoutService;
import com.example.paymentservice.service.PaymentProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/payments")
public class InternalPaymentController {

    private final PaymentProfileService paymentProfileService;
    private final CheckoutService checkoutService;

    public InternalPaymentController(
            PaymentProfileService paymentProfileService,
            CheckoutService checkoutService
    ) {
        this.paymentProfileService = paymentProfileService;
        this.checkoutService = checkoutService;
    }

    @PostMapping("/customers")
    public ResponseEntity<PaymentProfileResponse> provisionCustomer(
            @Valid @RequestBody ProvisionCustomerRequest request
    ) {
        PaymentProfileResponse response = paymentProfileService.provisionStripeCustomer(request);
        HttpStatus status = response.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/sessions")
    public ResponseEntity<CreateCheckoutSessionResponse> createInternalPaymentSession(
            @Valid @RequestBody InternalCreatePaymentSessionRequest request
    ) {
        CreateCheckoutSessionRequest checkoutRequest = new CreateCheckoutSessionRequest(
                request.patientId(),
                request.amount(),
                request.description(),
                request.currency()
        );

        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(checkoutService.createCheckoutSession(checkoutRequest));
        } catch (ResourceNotFoundException ex) {
            // Backfill missing payment profile for legacy users not yet provisioned.
            ProvisionCustomerRequest provisionRequest = new ProvisionCustomerRequest(
                    request.patientId(),
                    request.patientId() + "@example.com",
                    "Patient " + request.patientId(),
                    null,
                    "PATIENT"
            );
            paymentProfileService.provisionStripeCustomer(provisionRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(checkoutService.createCheckoutSession(checkoutRequest));
        }
    }
}
