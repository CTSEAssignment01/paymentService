package com.example.paymentservice.controller;

import com.example.paymentservice.dto.PaymentProfileResponse;
import com.example.paymentservice.dto.ProvisionCustomerRequest;
import com.example.paymentservice.service.InternalAuthService;
import com.example.paymentservice.service.PaymentProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/payments")
public class InternalPaymentController {

    private final PaymentProfileService paymentProfileService;
    private final InternalAuthService internalAuthService;

    public InternalPaymentController(PaymentProfileService paymentProfileService, InternalAuthService internalAuthService) {
        this.paymentProfileService = paymentProfileService;
        this.internalAuthService = internalAuthService;
    }

    @PostMapping("/customers")
    public ResponseEntity<PaymentProfileResponse> provisionCustomer(
            @RequestHeader("X-Internal-Api-Key") String internalApiKey,
            @Valid @RequestBody ProvisionCustomerRequest request
    ) {
        internalAuthService.verifyInternalApiKey(internalApiKey);
        PaymentProfileResponse response = paymentProfileService.provisionStripeCustomer(request);
        HttpStatus status = response.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}
