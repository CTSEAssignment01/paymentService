package com.example.paymentservice.service;

import com.example.paymentservice.dto.PaymentProfileResponse;
import com.example.paymentservice.dto.ProvisionCustomerRequest;
import com.example.paymentservice.exception.ExternalServiceException;
import com.example.paymentservice.exception.ResourceNotFoundException;
import com.example.paymentservice.model.PaymentProfile;
import com.example.paymentservice.repository.PaymentProfileRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.param.CustomerCreateParams;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentProfileService {

    private final PaymentProfileRepository paymentProfileRepository;

    public PaymentProfileService(PaymentProfileRepository paymentProfileRepository) {
        this.paymentProfileRepository = paymentProfileRepository;
    }

    @Transactional
    public PaymentProfileResponse provisionStripeCustomer(ProvisionCustomerRequest request) {
        Optional<PaymentProfile> existing = paymentProfileRepository.findByUserId(request.userId());
        if (existing.isPresent()) {
            return toResponse(existing.get(), false);
        }

        try {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("userId", request.userId().toString());
            if (request.role() != null && !request.role().isBlank()) {
                metadata.put("role", request.role());
            }

                CustomerCreateParams.Builder paramsBuilder = CustomerCreateParams.builder()
                    .setEmail(request.email())
                    .setName(request.name())
                    .putAllMetadata(metadata);

                if (request.phone() != null && !request.phone().isBlank()) {
                paramsBuilder.setPhone(request.phone());
                }

                CustomerCreateParams params = paramsBuilder.build();

            Customer customer = Customer.create(params);

            PaymentProfile profile = new PaymentProfile();
            profile.setUserId(request.userId());
            profile.setEmail(request.email());
            profile.setName(request.name());
            profile.setPhone(request.phone());
            profile.setStripeCustomerId(customer.getId());

            PaymentProfile saved = paymentProfileRepository.save(profile);
            return toResponse(saved, true);
        } catch (StripeException ex) {
            throw new ExternalServiceException("Failed to provision Stripe customer", ex);
        }
    }

    public PaymentProfileResponse getByUserId(UUID userId) {
        PaymentProfile profile = paymentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment profile not found for user: " + userId));
        return toResponse(profile, false);
    }

    public PaymentProfile getEntityByUserId(UUID userId) {
        return paymentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment profile not found for user: " + userId));
    }

    private PaymentProfileResponse toResponse(PaymentProfile profile, boolean created) {
        return new PaymentProfileResponse(
                profile.getUserId(),
                profile.getEmail(),
                profile.getName(),
                profile.getPhone(),
                profile.getStripeCustomerId(),
                created,
                profile.getCreatedAt()
        );
    }
}
