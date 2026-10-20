# Event cancellation

`POST /api/events/{eventId}/cancel` is organizer-only and returns `204` once
cancellation and its durable notification fact commit. Repeating it does not
create another audit entry or notification campaign, including after the
original event end time. A first cancellation after the event ended is rejected.
Cancellation is terminal; publication and subsequent editing remain forbidden.

## Admission and orders

Cancellation immediately closes registration and admission. Lifecycle changes,
checkout issuance, and check-in use the same event transaction lock so issuance
or admission cannot race past a committed cancellation. Existing PDF/QR copies
cannot be recalled: online validation remains authoritative. Newly generated
ticket PDFs show cancellation and omit admission QR codes; QR downloads are
rejected.

The checkout expiry worker closes pending checkouts as `CANCELLED`, releases
reservations, and abandons their payments on its next pass (normally within five
seconds). Those operations are retried after failure. An unfulfilled late bank
capture is compensated through the existing payment saga; it does not issue
admission to the cancelled event. The bank may require reconciliation before a
void or refund is confirmed.

Completed orders and their financial history are retained. Event cancellation
**does not automatically refund completed orders or the paid-sales activation
fee**. Existing organizer-initiated full-refund eligibility and the bank outcome
remain authoritative. The cancellation email does not promise a refund.

## Notifications and calendars

The transactional outbox fans cancellation out to current attendee email
addresses (including unclaimed, checked-in, and refund-pending tickets) plus
activated organizer/staff email addresses published by IAM. Already cancelled
or refunded tickets are excluded. One notice is reserved per event and normalized
email address, even when the same person has multiple tickets or staff roles.
No account or ticket claim is required for an attendee to receive the notice.

Each notice includes an `.ics` attachment with a stable event UUID-based UID,
`STATUS:CANCELLED`, a cancellation sequence, and UTC event times. Calendar import
is a user action; BiletFlow does not have write access to the user's calendar.

Recipient fan-out and outbox acknowledgement commit together. SMTP runs outside
the database transaction. Notices survive restarts, use a two-minute recoverable
lease, and retry after 30 seconds with exponential backoff capped at one hour.
An expired worker cannot acknowledge a replacement worker's claim. Retries keep
the same Message-ID. Delivery is at least once: an SMTP acceptance followed by a
crash can produce another copy of the same notice.

`biletflow.events.cancellation-mail-enabled` defaults to `true` and controls the
SMTP worker. `cancellation-mail-delay-ms` defaults to `5000` and
`cancellation-mail-initial-delay-ms` to `10000`, under the same prefix. Disabling
the worker retains queued notices. Monitor the outbox and
`event_management_cancellation_notice` for unresolved work and SMTP failures.

Ticket-confirmation delivery skips cancelled events. A confirmation already in
flight may still arrive; the cancellation notice and online admission state
remain authoritative. Historical cancellations are not automatically mailed by
deploying this feature; only newly committed cancellation facts create campaigns.

## Verification

Run `./gradlew test integrationTest` with the project's Java toolchain and Docker.
Focused tests cover authorization, idempotent cancellation, outbox rollback,
late-payment compensation, retained completed orders, notification recipients,
SMTP retry, worker lease recovery, and calendar escaping/UTF-8 line folding.

With the development server running, a fully automated free-registration test
creates its own activated accounts and event, then verifies cancellation,
blocked sales/admission/QR downloads, retained order history, and the attendee
cancellation email captured by Mailpit:

```sh
CANCEL_EVENT=1 PRICE_KZT=0 bash scripts/e2e/biletflow-run-e2e.sh
```

Omit `PRICE_KZT=0` to also exercise the Halyk activation and ticket payments;
those payments require the sandbox browser steps. The script uses fresh fixture
events and leaves cancellation records available for inspection.

