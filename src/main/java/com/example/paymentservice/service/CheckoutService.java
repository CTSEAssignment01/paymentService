package com.example.paymentservice.service;

import com.example.paymentservice.config.StripeProperties;
import com.example.paymentservice.dto.CreateCheckoutSessionRequest;
import com.example.paymentservice.dto.CreateCheckoutSessionResponse;
import com.example.paymentservice.dto.PaymentTransactionResponse;
import com.example.paymentservice.exception.BadRequestException;
import com.example.paymentservice.exception.ExternalServiceException;
import com.example.paymentservice.exception.ResourceNotFoundException;
import com.example.paymentservice.model.PaymentProfile;
import com.example.paymentservice.model.PaymentStatus;
import com.example.paymentservice.model.PaymentTransaction;
import com.example.paymentservice.repository.PaymentTransactionRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class CheckoutService {

    private final StripeProperties stripeProperties;
    private final PaymentProfileService paymentProfileService;
    private final PaymentTransactionRepository paymentTransactionRepository;

    public CheckoutService(
            StripeProperties stripeProperties,
            PaymentProfileService paymentProfileService,
            PaymentTransactionRepository paymentTransactionRepository
    ) {
        this.stripeProperties = stripeProperties;
        this.paymentProfileService = paymentProfileService;
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    @Transactional
    public CreateCheckoutSessionResponse createCheckoutSession(CreateCheckoutSessionRequest request) {
        log.info("Creating checkout session for userId={}, amount={}, currency={}", request.userId(), request.amount(), request.currency());

        PaymentProfile profile = paymentProfileService.getEntityByUserId(request.userId());

        String currency = request.currency() == null || request.currency().isBlank()
                ? stripeProperties.defaultCurrency()
                : request.currency().toLowerCase(Locale.ROOT);

        long amountInMinor = toMinorUnits(request.amount());

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setCustomer(profile.getStripeCustomerId())
                    .setSuccessUrl(stripeProperties.successUrl())
                    .setCancelUrl(stripeProperties.cancelUrl())
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency(currency)
                                                    .setUnitAmount(amountInMinor)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName(request.description())
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
                    .build();

            Session stripeSession = Session.create(params);

            PaymentTransaction tx = new PaymentTransaction();
            tx.setUserId(request.userId());
            tx.setAppointmentId(request.appointmentId());
            tx.setStripeCustomerId(profile.getStripeCustomerId());
            tx.setStripeSessionId(stripeSession.getId());
            tx.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
            tx.setCurrency(currency);
            tx.setDescription(request.description());
            tx.setStatus(PaymentStatus.PENDING);
            paymentTransactionRepository.save(tx);

            log.info("Checkout session created successfully: {}", stripeSession.getId());
            return new CreateCheckoutSessionResponse(stripeSession.getId(), stripeSession.getUrl(), PaymentStatus.PENDING.name());
        } catch (StripeException ex) {
            log.error("Stripe error while creating checkout session", ex);
            throw new ExternalServiceException("Failed to create Stripe checkout session", ex);
        } catch (Exception ex) {
            log.error("Unexpected error while creating checkout session", ex);
            throw new ExternalServiceException("Failed to create checkout session", ex);
        }
    }

    @Transactional
    public void handleStripeWebhook(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new BadRequestException("Stripe-Signature header is missing");
        }
        if (stripeProperties.webhookSecret() == null || stripeProperties.webhookSecret().isBlank()) {
            throw new BadRequestException("Stripe webhook secret is not configured");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, stripeProperties.webhookSecret());
        } catch (Exception ex) {
            throw new BadRequestException("Invalid Stripe webhook signature");
        }

        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        if (dataObjectDeserializer.getObject().isEmpty()) {
            return;
        }

        Object stripeObject = dataObjectDeserializer.getObject().get();
        if (stripeObject instanceof Session session) {
            paymentTransactionRepository.findByStripeSessionId(session.getId()).ifPresent(tx -> {
                switch (event.getType()) {
                    case "checkout.session.completed" -> tx.setStatus(PaymentStatus.COMPLETED);
                    case "checkout.session.expired" -> tx.setStatus(PaymentStatus.EXPIRED);
                    case "checkout.session.async_payment_failed" -> tx.setStatus(PaymentStatus.FAILED);
                    default -> {
                        return;
                    }
                }
                paymentTransactionRepository.save(tx);
            });
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentTransactionResponse> getTransactionsForUser(UUID userId) {
        return paymentTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PaymentTransactionResponse confirmCheckoutSession(String sessionId) {
        PaymentTransaction tx = paymentTransactionRepository.findByStripeSessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found for session: " + sessionId));

        try {
            Session session = Session.retrieve(sessionId);
            String paymentStatus = session.getPaymentStatus();
            String sessionStatus = session.getStatus();

            if ("paid".equalsIgnoreCase(paymentStatus)) {
                tx.setStatus(PaymentStatus.COMPLETED);
            } else if ("expired".equalsIgnoreCase(sessionStatus)) {
                tx.setStatus(PaymentStatus.EXPIRED);
            } else if ("unpaid".equalsIgnoreCase(paymentStatus)) {
                tx.setStatus(PaymentStatus.FAILED);
            } else {
                tx.setStatus(PaymentStatus.PENDING);
            }
        } catch (StripeException ex) {
            throw new ExternalServiceException("Failed to verify checkout session with Stripe", ex);
        }

        PaymentTransaction saved = paymentTransactionRepository.save(tx);
        return toResponse(saved);
    }

    private PaymentTransactionResponse toResponse(PaymentTransaction tx) {
        return new PaymentTransactionResponse(
                tx.getId(),
                tx.getUserId(),
                tx.getAppointmentId(),
                tx.getStripeSessionId(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getDescription(),
                tx.getStatus().name(),
                tx.getCreatedAt(),
                tx.getUpdatedAt()
        );
    }

    private long toMinorUnits(BigDecimal amount) {
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        if (scaled.signum() <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }
        return scaled.movePointRight(2).longValueExact();
    }
}
