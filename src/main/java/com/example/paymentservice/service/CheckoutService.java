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
import java.util.Map;
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
                    .putMetadata("userId", request.userId().toString())
                    .putMetadata("appointmentId", request.appointmentId() != null ? request.appointmentId().toString() : "")
                    .putMetadata("currency", currency)
                    .putMetadata("description", request.description())
                    .putMetadata("amount", request.amount().toPlainString())
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

            // DO NOT persist transaction yet; only persist when confirmed (via webhook or confirm endpoint)
            // This ensures only COMPLETED/final status records exist in the database
            log.info("Checkout session created successfully: {}", stripeSession.getId());
            return new CreateCheckoutSessionResponse(stripeSession.getId(), stripeSession.getUrl(), "PENDING");
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
            switch (event.getType()) {
                case "checkout.session.completed" -> {
                    // Create and persist transaction only upon successful payment
                    paymentTransactionRepository.findByStripeSessionId(session.getId())
                        .ifPresentOrElse(
                            tx -> {
                                tx.setStatus(PaymentStatus.COMPLETED);
                                paymentTransactionRepository.save(tx);
                            },
                            () -> createAndSaveTransaction(session, PaymentStatus.COMPLETED)
                        );
                }
                case "checkout.session.expired" -> {
                    paymentTransactionRepository.findByStripeSessionId(session.getId())
                        .ifPresentOrElse(
                            tx -> {
                                tx.setStatus(PaymentStatus.EXPIRED);
                                paymentTransactionRepository.save(tx);
                            },
                            () -> createAndSaveTransaction(session, PaymentStatus.EXPIRED)
                        );
                }
                case "checkout.session.async_payment_failed" -> {
                    paymentTransactionRepository.findByStripeSessionId(session.getId())
                        .ifPresentOrElse(
                            tx -> {
                                tx.setStatus(PaymentStatus.FAILED);
                                paymentTransactionRepository.save(tx);
                            },
                            () -> createAndSaveTransaction(session, PaymentStatus.FAILED)
                        );
                }
            }
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
        try {
            Session session = Session.retrieve(sessionId);
            String paymentStatus = session.getPaymentStatus();
            String sessionStatus = session.getStatus();

            PaymentStatus finalStatus;
            if ("paid".equalsIgnoreCase(paymentStatus)) {
                finalStatus = PaymentStatus.COMPLETED;
            } else if ("expired".equalsIgnoreCase(sessionStatus)) {
                finalStatus = PaymentStatus.EXPIRED;
            } else if ("unpaid".equalsIgnoreCase(paymentStatus)) {
                finalStatus = PaymentStatus.FAILED;
            } else {
                // If status is still unknown, don't persist yet
                throw new BadRequestException("Cannot confirm session; payment status is still unknown");
            }

            // Find existing transaction or create a new one with final status
            PaymentTransaction tx = paymentTransactionRepository.findByStripeSessionId(sessionId)
                    .orElseGet(() -> createNewTransaction(session, finalStatus));
            
            // Update status if not already set
            if (tx.getStatus() == null) {
                tx.setStatus(finalStatus);
            }
            
            PaymentTransaction saved = paymentTransactionRepository.save(tx);
            return toResponse(saved);
        } catch (StripeException ex) {
            throw new ExternalServiceException("Failed to verify checkout session with Stripe", ex);
        }
    }

    private void createAndSaveTransaction(Session session, PaymentStatus status) {
        try {
            Map<String, String> metadata = session.getMetadata();
            if (metadata == null || !metadata.containsKey("userId")) {
                log.warn("Cannot create transaction: missing userId in Stripe session metadata");
                return;
            }

            UUID userId = UUID.fromString(metadata.get("userId"));
            UUID appointmentId = metadata.containsKey("appointmentId") && !metadata.get("appointmentId").isBlank()
                    ? UUID.fromString(metadata.get("appointmentId"))
                    : null;
            String currency = metadata.getOrDefault("currency", "usd");
            String description = metadata.getOrDefault("description", "Payment");
            BigDecimal amount = new BigDecimal(metadata.getOrDefault("amount", "0"));

            PaymentTransaction tx = new PaymentTransaction();
            tx.setUserId(userId);
            tx.setAppointmentId(appointmentId);
            tx.setStripeCustomerId(session.getCustomer());
            tx.setStripeSessionId(session.getId());
            tx.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
            tx.setCurrency(currency);
            tx.setDescription(description);
            tx.setStatus(status);
            paymentTransactionRepository.save(tx);

            log.info("Created payment transaction for sessionId={} with status={}", session.getId(), status);
        } catch (Exception ex) {
            log.error("Failed to create transaction from webhook for sessionId={}", session.getId(), ex);
        }
    }

    private PaymentTransaction createNewTransaction(Session session, PaymentStatus status) {
        Map<String, String> metadata = session.getMetadata();
        if (metadata == null || !metadata.containsKey("userId")) {
            throw new BadRequestException("Cannot confirm session: missing user information in session metadata");
        }

        UUID userId = UUID.fromString(metadata.get("userId"));
        UUID appointmentId = metadata.containsKey("appointmentId") && !metadata.get("appointmentId").isBlank()
                ? UUID.fromString(metadata.get("appointmentId"))
                : null;
        String currency = metadata.getOrDefault("currency", "usd");
        String description = metadata.getOrDefault("description", "Payment");
        BigDecimal amount = new BigDecimal(metadata.getOrDefault("amount", "0"));

        PaymentTransaction tx = new PaymentTransaction();
        tx.setUserId(userId);
        tx.setAppointmentId(appointmentId);
        tx.setStripeCustomerId(session.getCustomer());
        tx.setStripeSessionId(session.getId());
        tx.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        tx.setCurrency(currency);
        tx.setDescription(description);
        tx.setStatus(status);
        return tx;
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
