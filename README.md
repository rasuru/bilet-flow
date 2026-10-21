# BiletFlow

BiletFlow is a self-service event ticketing platform for Kazakhstan. Organizers publish events, sell tickets in KZT, manage admission, issue refunds, and request simulated payouts. Attendees register or purchase tickets and receive a PDF ticket with a QR code by email.

This repository contains the backend API. Customer and organizer frontends are separate applications; the included payment pages are development helpers.

## Features

- **Accounts and access:** registration, email activation, JWT authentication, and organizer/staff authorization.
- **Events:** event setup, publication, public/unlisted visibility, and cancellation with attendee notifications.
- **Ticketing:** ticket types, general admission and assigned seating, temporary inventory holds, ticket issuance, claiming, and online check-in.
- **Checkout:** free registration and paid purchases through Halyk's hosted sandbox, with a per-event paid-sales activation fee.
- **Payments and refunds:** verified bank status, durable reconciliation, full refunds, ticket invalidation, and purchaser refund receipts.
- **Ticket delivery:** printable PDF tickets, QR downloads, and queued email delivery with retries.
- **Organizer payouts:** available balance, idempotent payout requests, history, simulated processing, and email notices. Balances account for fees, refunds, reserved funds, and deficits after payout.

Payments use **Halyk sandbox**. Organizer payouts are **internal simulations** and do not transfer money to a bank account. Event cancellation closes sales and admission but does not automatically refund completed orders.

## Architecture

Java / Spring Boot modular monolith, originally generated with JHipster and organized by DDD bounded contexts:

| Package                | Responsibility                                                                        |
| ---------------------- | ------------------------------------------------------------------------------------- |
| `iam`                  | Accounts, authentication, users, and access.                                          |
| `eventmanagement`      | Event details, publication, lifecycle, and organizer/staff access.                    |
| `ticketinventory`      | Ticket types, seats, holds, issuance, documents, and admission.                       |
| `ordercheckout`        | Checkout sessions, orders, immutable purchase snapshots, and purchase coordination.   |
| `paymentsmonetization` | Halyk integration, activation fees, payments, refunds, ledger, and simulated payouts. |
| `common`               | Shared primitives, security, configuration, and infrastructure.                       |

Within a context, domain code enforces business rules; application services coordinate use cases and transactions; REST, persistence, and infrastructure adapt HTTP, storage, and external services. Contexts expose synchronous Open Host Services (OHS) rather than sharing internal repositories. Durable integration events and background workers handle asynchronous workflows and retries.

```text
src/main/java/com/biletflow/biletflow/  Bounded contexts and shared code
src/main/resources/config/            Application configuration and Liquibase
src/main/docker/                     Development dependencies and optional tools
src/test/                            Unit and integration tests
scripts/e2e/                         API-driven end-to-end journeys
docs/                                Setup, conventions, and feature guides
```

## Local development

Requires JDK 21 or 25, Docker Compose, Bash, and OpenSSL. Use the included Gradle wrapper.

```bash
bash dev.sh init
# Add Halyk sandbox credentials to .env.local for paid checkout.
bash dev.sh
```

The launcher loads local settings, starts dependencies, and runs the development backend. Free registration works without Halyk credentials. Keep the generated encryption key while retaining the database.

| Resource                | Local address                       |
| ----------------------- | ----------------------------------- |
| API                     | <http://localhost:8080/api>         |
| OpenAPI JSON            | <http://localhost:8080/v3/api-docs> |
| Development email inbox | <http://localhost:8025>             |

See [local development](docs/development.md) for dependency setup, environment settings, and payment helper pages. PostgreSQL, Redis, Elasticsearch, and Mailpit must be enabled in `src/main/docker/services.yml` for the current development configuration.

## Testing

```bash
./gradlew test integrationTest
```

Integration tests use Testcontainers and require Docker. They do not require Halyk credentials.

With the development server running, test free registration and event cancellation:

```bash
CANCEL_EVENT=1 PRICE_KZT=0 bash scripts/e2e/biletflow-run-e2e.sh
```

Test the complete paid lifecycle, including simulated payout and refund:

```bash
PAYOUT=1 REFUND_ORDER=1 bash scripts/e2e/biletflow-run-e2e.sh
```

The E2E scripts create fresh accounts and events and verify ticket documents and email delivery. Paid tests require Halyk sandbox credentials and browser payment steps. See [the payment guide](docs/payment-saga.md) for details.

## API integration

Frontends use `http://localhost:8080/api` locally. Obtain a JWT through `POST /api/authenticate` and include it in authenticated requests:

```http
Authorization: Bearer <JWT>
```

Use the development OpenAPI document for endpoint contracts and request/response schemas. Workflow details and retry semantics are documented in the feature guides below.

## Contributor guides

- [Project conventions](docs/project-conventions.md)
- [Local development](docs/development.md)
- [Database migrations](docs/db-migrations-guide.md)
- [PostgreSQL guide](docs/psql-guide.md)
- [Payments, refunds, and simulated payouts](docs/payment-saga.md)
- [Ticket documents and email delivery](docs/ticket-delivery.md)
- [Event cancellation](docs/event-cancellation.md)
- [Halyk invoice identifiers](docs/halyk-invoice-identifiers.md)
- [Generated JHipster reference](docs/jhipster-readme.md)

## Build

Build a production-profile JAR:

```bash
./gradlew -Pprod clean bootJar
```

Run the resulting JAR with the target environment's database, cache, search, SMTP, and payment configuration. The development Compose files and sample secrets are intended for local use.
