# Payment Service

Spring Boot microservice for payment onboarding and Stripe sandbox checkout.

## What This Service Does

- Creates and stores Stripe customer IDs for users (idempotent flow).
- Exposes an internal endpoint for service-to-service calls after user registration.
- Creates Stripe Checkout sessions for sandbox testing.
- Handles Stripe webhook events to update payment status.
- Runs as an independent Dockerized microservice for Choreo deployment.

## Core Integration Requirement (User -> Payment)

After a user is successfully registered in `userService`, `userService` should call:

- `POST /api/internal/payments/customers`

Example payload:

```json
{
  "userId": "7f2b1c69-3a6e-4ebf-8f31-c8757d5cc5cb",
  "email": "user@example.com",
  "name": "Test User",
  "phone": "+94771234567",
  "role": "PATIENT"
}
```

Response (created):

```json
{
  "userId": "7f2b1c69-3a6e-4ebf-8f31-c8757d5cc5cb",
  "email": "user@example.com",
  "name": "Test User",
  "phone": "+94771234567",
  "stripeCustomerId": "cus_123456789",
  "created": true,
  "createdAt": "2026-03-20T10:30:12.123"
}
```

If the same user is sent again, the endpoint returns the existing Stripe customer with `created: false`.

## API Endpoints

- `POST /api/internal/payments/customers` (internal, API key protected)
- `GET /api/payments/customers/{userId}`
- `POST /api/payments/checkout-session`
- `POST /api/payments/webhooks/stripe`
- `GET /actuator/health`

## Environment Variables

- `PORT` (default: `8085`)
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `STRIPE_API_KEY` (Stripe test key: starts with `sk_test_`)
- `STRIPE_WEBHOOK_SECRET`
- `STRIPE_SUCCESS_URL`
- `STRIPE_CANCEL_URL`
- `STRIPE_DEFAULT_CURRENCY` (default: `usd`)

## Run Locally

```bash
mvn clean spring-boot:run
```

## Build and Test

```bash
mvn clean test
mvn clean package -DskipTests
```

## Docker

Build image:

```bash
docker build -t payment-service:local .
```

Run container:

```bash
docker run -p 8085:8085 \
  -e DB_URL="jdbc:postgresql://host.docker.internal:5432/ctse" \
  -e DB_USERNAME="postgres" \
  -e DB_PASSWORD="postgres" \
  -e STRIPE_API_KEY="sk_test_xxx" \
  payment-service:local
```

## Choreo Deployment Notes

1. Push `paymentService` code to your Git repository.
2. In Choreo, create a new Service component from this repository path.
3. Select Dockerfile-based build.
4. Configure all required environment variables (especially secrets).
5. Expose port `8085` and verify `/actuator/health`.

## DevSecOps Baseline

- Secrets are externalized through environment variables.
- Internal onboarding endpoint requires API key.
- Stripe webhook signature verification is enforced.
- Container runs as non-root user.
- Health endpoint available for orchestrator checks.

To satisfy assignment DevSecOps evidence, integrate one managed SAST scanner in CI (e.g., SonarCloud or Snyk) using repository secrets.
