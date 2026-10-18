# Payment saga: Halyk sandbox

This implements the backend purchase lifecycle: free registration, paid checkout, Halyk hosted payment, verified capture, ticket issuance, cancellation, full refunds, recovery, and the per-event activation-fee prerequisite. Financial status responses are labelled as sandbox data. The HTML tester is a developer aid, not the customer frontend.

## Architecture

- **Order & Checkout** owns checkout sessions, the commercial order, immutable price/attendee/hold snapshots, and the process manager. The checkout UUID is also the eventual order UUID. One idempotency key creates at most one checkout, payment and order.
- **Payments & Monetization** owns payment/refund aggregates, activation payments, bank interaction, reconciliation, and the financial ledger. Its application port translates Halyk states into our own financial vocabulary.
- **Ticket Inventory** owns reservations, ticket issuance, admission and inventory reversal. All calls from O&C use the published `CheckoutInventoryOhs` contract. Prices are loaded from TI, never accepted from the client.
- **Event Management** publishes event sales/authorization facts through its OHS. Payment code does not import EM domain objects.
- **common/events** contains a PostgreSQL transactional outbox. PM publishes payment outcome facts in the same transaction as financial state. One durable consumer per event type handles them; its local effects and acknowledgement commit together. Failures roll back and retry after 30 seconds indefinitely. A broker is unnecessary for this modular monolith.

Repositories keep explicit JSON persistence DTOs in BC-owned tables, with relational identifiers, indexes and uniqueness constraints. Domain aggregates have no Spring/JPA/Jackson dependencies. The original OC repository interfaces now have JDBC adapters. PM execution metadata is persisted separately from its financial domain state through an application repository port. Local OHS effects use the shared Spring/JPA database transaction; HTTP requests never hold database transactions open.

## Setup

Use Java 21 and the repository's normal PostgreSQL/Redis/Elasticsearch services. The new Liquibase migration is included from `master.xml`; do not regenerate or edit the existing migration.

Example environment in bash:

```bash
export BILETFLOW_PAYMENTS_HALYK_CLIENT_ID=test
export BILETFLOW_PAYMENTS_HALYK_CLIENT_SECRET='some-secret'
export BILETFLOW_PAYMENTS_HALYK_TERMINAL_ID=insert-id-here
export BILETFLOW_PAYMENTS_SESSION_ENCRYPTION_KEY="$(openssl rand -base64 32)"
export BILETFLOW_PAYMENTS_PUBLIC_BASE_URL=http://localhost:8080
export BILETFLOW_PAYMENTS_RETURN_URL=http://localhost:9090/halyk-sandbox-checkout.html
./gradlew
```

These are Halyk's **public test credentials**, not production merchant credentials. For fish, use `set -gx NAME value` instead of `export NAME=value`; generate the key with `set -gx BILETFLOW_PAYMENTS_SESSION_ENCRYPTION_KEY (openssl rand -base64 32)`.

Save the generated encryption key securely and reuse it after restarts and across backend replicas. Changing it makes existing encrypted payment forms unreadable. Merchant credentials never reach the browser. The limited payment form token is encrypted with AES-GCM before persistence and reused for the same invoice; card numbers, CVC, card masks and raw callback/status payloads are not stored.

For real callback delivery, set `PUBLIC_BASE_URL` to the externally reachable HTTPS origin forwarding to this backend. The callback path is `/api/payments/halyk/callback`. Localhost is sufficient to exercise payment through scheduled status reconciliation even when Halyk cannot call back. The browser return URL can remain local for local testing. Browser redirects never change financial state.

Serve the tester in another terminal:

```bash
python3 -m http.server 9090 --directory docs
```

Open `http://localhost:9090/halyk-sandbox-checkout.html`. Port 9090 is already allowed by the repository's development CORS configuration. The tester keeps the JWT only in the page inputs, without local storage or logging.

Configuration defaults: activation fee **1,000 KZT**; estimated processing fee **3%**, retained in the estimate after a refund. Set `biletflow.payments.activation-fee-kzt` and `biletflow.payments.processing-fee-rate` to change those demonstration assumptions. They are not claimed to be Halyk's actual fees. Paid checkout is at least 10 KZT because the documented minimum refund is 10 KZT. Free checkout has no payment or fee.

## End-to-end test

Use an organizer JWT in `$ORGANIZER_JWT`, an attendee JWT in `$ATTENDEE_JWT`, a published event UUID in `$EVENT_ID`, and a currently purchasable ticket-type UUID in `$TICKET_TYPE_ID`. Set `$API=http://localhost:8080` and obtain JWTs through the existing login endpoint. Assigned-seat setup uses the existing event/ticket-type APIs.

1. As organizer, request per-event activation. Verification is explicitly simulated for this university MVP; acceptance of the sandbox paid-event terms is recorded.

```bash
curl -sS -X POST "$API/api/payments/events/$EVENT_ID/activation" \
  -H "Authorization: Bearer $ORGANIZER_JWT" -H 'Content-Type: application/json' \
  -d '{"acceptSandboxTerms":true,"simulateIdentityAndPayoutVerification":true}'
```

Paste the returned `paymentId` and organizer JWT into the tester and open Halyk payment. Use a current test card from Halyk's official test-data page below. After paying, check:

```bash
curl -sS "$API/api/payments/events/$EVENT_ID/activation" -H "Authorization: Bearer $ORGANIZER_JWT"
```

Continue when activation is `ACTIVE`. Repeated activation requests reuse an unresolved payment. A failed or expired fee payment can be replaced; the old invoice remains monitored and any late charge is compensated rather than activating a second time.

2. As attendee, reserve and start checkout. Choose a new UUID as `$CHECKOUT_KEY` for each new purchase. For a retry after a timeout, reuse the same key and identical JSON.

```bash
curl -sS -X POST "$API/api/checkouts" \
  -H "Authorization: Bearer $ATTENDEE_JWT" -H "Idempotency-Key: $CHECKOUT_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"eventId":"'"$EVENT_ID"'","selections":[{"ticketTypeId":"'"$TICKET_TYPE_ID"'","seats":[],"attendeeEmails":["attendee@example.com"]}]}'
```

For assigned seating, provide one seat UUID per email in matching array order. Group a ticket type into one selection. The entire request is limited to 100 tickets and 30 ticket types. The returned `amountKzt` is the frozen commercial snapshot; later ticket-type price changes do not change this purchase. There is no promotions BC in this archive, so this implementation does not accept arbitrary client discounts or pretend a promotion was reserved.

A zero-price checkout returns `COMPLETED` immediately with tickets. A paid checkout returns `AWAITING_PAYMENT` and `paymentId`. Holds retain TI's existing ten-minute deadline.

3. Paste that payment ID and attendee JWT into the tester. Pay on Halyk's hosted test page. For a real frontend, POST `/api/payments/{paymentId}/session`, load its fixed `scriptUrl` and call `halyk.pay(result.paymentObject)`. Never send merchant credentials from frontend code. Poll the checkout until its order outcome is available:

```bash
curl -sS "$API/api/checkouts/$CHECKOUT_KEY" -H "Authorization: Bearer $ATTENDEE_JWT"
```

Tickets appear only after bank status confirms capture and the local sale transaction commits. Returned tickets include their attendee email, seat, ticket type, ticket ID, and QR UUID. Tickets bought for someone else are not automatically linked to the purchaser; the existing `/api/tickets/{id}/claim` flow checks the attendee's verified email.

4. As organizer, request a full refund of unused tickets:

```bash
curl -sS -X POST "$API/api/checkouts/$CHECKOUT_KEY/refund" \
  -H "Authorization: Bearer $ORGANIZER_JWT" -H 'Content-Type: application/json' \
  -d '{"reason":"Customer requested a full refund"}'
```

`refundState=PENDING` means admission is blocked and the bank outcome is still unresolved. Inventory remains sold until the bank confirms the refund. An acknowledged full-refund request followed by matching bank refund status sets `refundState=REFUNDED`, cancels the commercial order, invalidates tickets and reverses inventory once. A definitively rejected refund can be retried with a new refund ID. The history of earlier attempts is retained. Refunds can be initiated after event cancellation too.

The MVP policy allows organizers to refund only unused tickets. It does not grant automatic attendee refunds or partial refunds. A refund initiated outside this application is detected and sent for operator review: Halyk status `REFUND` alone does not prove whether a full or partial refund occurred. After independently confirming the full amount with the bank, the audited operator resolution below invalidates admission, including an already-used ticket if necessary to reflect the financial truth.

5. Check organizer accounting:

```bash
curl -sS "$API/api/payments/revenue/mine" -H "Authorization: Bearer $ORGANIZER_JWT"
```

Charge and refund ledger entries are immutable and have unique operation keys. Activation fees do not count as organizer ticket revenue. A full refund can leave a negative estimated net amount representing retained fees. This is an accounting summary, not a bank settlement balance. Organizer payouts and bank-account verification remain separate monetization features; this change does not implement real payouts.

## Failure and recovery behavior

| Situation | Behavior |
| --- | --- |
| Duplicate checkout request | Same key and same authenticated owner/request return the original purchase; changed input conflicts. |
| Duplicate/reordered outcome | Deterministic event IDs and idempotent saga transitions prevent duplicate tickets, orders and ledger entries. |
| Callback | Constant-time validation of per-payment `secret_hash`; the callback only schedules a server-side status query. |
| Missing/forged browser success | No purchase confirmation. Status is verified with Halyk independently. |
| Authorization (`AUTH`) | Not a completed payment. Capture while checkout is viable; void an abandoned or expired authorization. |
| Bank timeout / not found / in progress | Remains unresolved. A transport error cannot mark payment failed. |
| Expired/cancelled checkout with a late capture | No ticket issuance; durable full-refund compensation. Old invoices stay monitored. |
| Ticket issuance/DB error | Local transaction rolls back; the outcome remains in the outbox and retries. If the hold expires before fulfillment can commit, compensate the payment. |
| Bank mutation timeout or worker crash | The precommitted `SENT` marker prevents automatic repetition. Query status until resolved; after five minutes it appears as `UNKNOWN`. If a full-refund acknowledgement was lost and the bank later reports only `REFUND`, an operator must independently confirm the full amount. |
| Refund pending | Ticket locks and `REFUND_PENDING` block concurrent check-in. A verified rejection restores admission. |
| Already-expired/released holds | Compensation is idempotent; it cannot release another checkout's newer reservation. |
| Concurrent ticket claim/check-in/refund | Mutations acquire the same pessimistic ticket lock and refresh stale managed state before writing. |
| Wrong amount/currency/terminal/invoice/payment correlation | No fulfillment or additional bank mutation; requires operator review. |
| Public sandbox invoice collision | The bank `data.biletflowPaymentId` must match this payment as well as the invoice/amount/terminal. A different app's test transaction cannot fulfill this checkout. |
| Backend restart | Saga, payment/refund attempts, leases, sessions, audit and outbox survive in PostgreSQL. |
| Multiple workers | Conditional DB leases serialize reconciliation; outbox rows use `FOR UPDATE SKIP LOCKED`; aggregate transaction locks serialize local commands. |

The invoice sequence never wraps because Halyk also requires uniqueness of the last six digits. Do not reset/recycle it while old bank invoices exist. Public test credentials are shared; a dedicated sandbox terminal is preferable when available. The adapter has no configurable production endpoints.

## Operator actions

An admin can GET `/api/payments/operations` for data conflicts and mutations still unresolved after five minutes. GET `/api/payments/{id}` includes the current bank operation, its ID and state. Unauthorized users cannot inspect other people's payments, checkouts, tickets or revenue through these new APIs.

If bank support independently confirms an operation was **not executed**, POST `/api/payments/{id}/resolve-operation` as `ROLE_ADMIN`:

```json
{
  "expectedOperationId": "the-current-operation-uuid",
  "resolution": "CONFIRMED_NOT_EXECUTED_RETRY",
  "bankSupportReference": "Sandbox support case reference"
}
```

`FULL_REFUND_CONFIRMED_COMPLETED` records an independently confirmed **full** refund when the bank acknowledgement was lost or the refund was initiated outside this application. It posts the financial entries and publishes the outcome atomically. It must never be used for an external partial refund. For an unfulfilled checkout whose compensation definitively failed, the organizer can retry the full refund through the same checkout refund endpoint.

Other resolutions are `REFUND_CONFIRMED_REJECTED` for a definitively rejected ticket refund, and `CLEAR_REVIEW` after resolving an identity/status conflict. The expected operation ID prevents accidentally resolving a different operation. These actions are audited with the actor and evidence reference. The adapter checks captured/authorized amounts against the snapshot. It does not treat the mutable amount in a refund status as proof of the returned amount; full-refund scope comes from an acknowledged full-refund request or explicit bank evidence. A normal `CHARGE`/`AUTH` status alone is insufficient evidence that a prior refund/capture was not executed. The Halyk docs describe `externalID` as a merchant refund reference, not as an exactly-once guarantee, so this implementation deliberately does not blindly retry ambiguous mutations.

Audit records live in `common_financial_audit`; historical ticket refunds live in `pm_refund_history`. To inspect unresolved integration delivery, query `common_outbox` where `delivered_at is null`, ordered by `attempts desc`. There is no destructive automatic dead-letter discard.

## Tests and validation

Run with the project's normal Java 21/Docker setup:

```bash
./gradlew test --tests '*PaymentSagaTest' --tests '*HalykGatewayTest' --tests '*InventoryCompensationTest'
./gradlew integrationTest --tests '*PaymentPersistenceIT'
./gradlew test
```

The isolated saga tests use the real application/domain code and fake bank/inventory/repositories. They cover capture, void, refund uncertainty, late capture, delayed/reordered/duplicate outcomes, issuance failure, idempotency, callback authentication, authorization, activation compensation and admission blocking. The integration tests exercise producer rollback, JPA consumer rollback with outbox acknowledgement, concurrent leases, refresh of stale ticket state, actual JSON persistence and refund history against PostgreSQL.

Validation in the implementation environment: 21 isolated saga tests (59 assertions) and 124 existing/new domain tests passed using a local Java harness. New application sources passed a Java compilation check with framework signatures substituted because dependencies could not be downloaded. This is **not** a full Spring application build. Gradle could not download its distribution (`Network is unreachable`); the host has Java 17 rather than the project's Java 21. The real Jackson/HTTP adapter tests, PostgreSQL integration tests and live Halyk card flow remain to be run in the project environment. No live sandbox transaction was claimed.

## Halyk contracts used

Verified against Halyk's official documentation on 2026-10-18:

- [Hosted payment page, scoped token, required secret_hash, callback](https://epayment.kz/docs/platezhnaya-stranica)
- [Status query, resultCode vs transaction statusName](https://epayment.kz/docs/check-status-payment)
- [Capture an AUTH transaction](https://epayment.kz/docs/podtverzhdenie-chastichnoe-podtverzhdenie-operacii)
- [Void an AUTH transaction](https://epayment.kz/docs/cancel)
- [Full refund, transaction ID, externalID, minimum amount](https://epayment.kz/docs/vozvrat-chastichnyi-vozvrat)
- [Current sandbox credentials and test cards](https://epayment.kz/docs/Test-credentials)

The existing project email delivery and absent Promotions application are not changed. Purchase/refund confirmations are observable through authenticated status endpoints; connecting those committed outcomes to customer email is a separate delivery adapter.

