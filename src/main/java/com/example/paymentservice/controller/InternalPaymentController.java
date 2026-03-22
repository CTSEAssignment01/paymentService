package com.example.paymentservice.controller;

import com.example.paymentservice.dto.CreateCheckoutSessionRequest;
import com.example.paymentservice.dto.CreateCheckoutSessionResponse;
import com.example.paymentservice.dto.InternalCreatePaymentSessionRequest;
import com.example.paymentservice.dto.PaymentProfileResponse;
import com.example.paymentservice.dto.ProvisionCustomerRequest;
import com.example.paymentservice.exception.ResourceNotFoundException;
import com.example.paymentservice.service.CheckoutService;
import com.example.paymentservice.service.InternalAuthService;
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
    private final InternalAuthService internalAuthService;
    private final CheckoutService checkoutService;

    public InternalPaymentController(PaymentProfileService paymentProfileService, InternalAuthService internalAuthService, CheckoutService checkoutService) {
        this.paymentProfileService = paymentProfileService;
        this.internalAuthService = internalAuthService;
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
    public ResponseEntity<CreateCheckoutSessionResponse> createPaymentSession(
            @RequestHeader("X-Internal-Api-Key") String internalApiKey,
            @Valid @RequestBody InternalCreatePaymentSessionRequest request
    ) {
        internalAuthService.verifyInternalApiKey(internalApiKey);

        try {
            paymentProfileService.getEntityByUserId(request.patientId());
        } catch (ResourceNotFoundException ex) {
            paymentProfileService.provisionStripeCustomer(new ProvisionCustomerRequest(
                    request.patientId(),
                    request.patientId() + "@patient.local",
                    "Patient " + request.patientId().toString().substring(0, 8),
                    null,
                    "PATIENT"
            ));
        }
        
        // Create checkout session with patient's appointment details
        CreateCheckoutSessionRequest checkoutRequest = new CreateCheckoutSessionRequest(
                request.patientId(),
                request.amount(),
                request.description(),
            request.currency() != null ? request.currency() : "USD",
            request.appointmentId()
        );
        
        CreateCheckoutSessionResponse response = checkoutService.createCheckoutSession(checkoutRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
