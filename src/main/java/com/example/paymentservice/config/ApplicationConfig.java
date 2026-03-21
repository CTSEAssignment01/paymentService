package com.example.paymentservice.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StripeProperties.class)
public class ApplicationConfig {

    private final StripeProperties stripeProperties;

    public ApplicationConfig(StripeProperties stripeProperties) {
        this.stripeProperties = stripeProperties;
    }

    @PostConstruct
    void initializeStripe() {
        if (stripeProperties.apiKey() != null && !stripeProperties.apiKey().isBlank()) {
            Stripe.apiKey = stripeProperties.apiKey();
        }
    }
}
