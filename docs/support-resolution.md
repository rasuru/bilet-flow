# Operational support cases

This API augments the existing support conversation, status, assignment and escalation endpoints. It is intended for the organizer's support workspace and the platform administration workspace. No new database tables, migrations or infrastructure are needed. The existing support audit table stores action requests and completed local actions.

## Read the case context

`GET /api/support/cases/{caseId}/context`

Use the authenticated agent's bearer token. The requester and users who cannot handle the case receive 403. A missing case receives 404. The case's stored references, not IDs from the client, determine the records to inspect.

The existing `GET /api/support/cases/{caseId}` still supplies the conversation and case status. Fetch the context separately for staff. A resolved case may be inspected but cannot execute actions until reopened.

The context contains these fields:

- `caseId`, `requesterUserId`, `requesterEmail` (verified email, nullable).
- `eventId` and `event` (title, startsAt, endsAt, venue, address, cancelled, suspended). A draft may have no venue. An unlinked platform case has no event.
- `linkedTicketId` (nullable).
- `order` (nullable): `id`, `eventId`, `ownerUserId`, `status`, `amountKzt`, `paymentId`, `refundState`, `refundId`, `createdAt`, and `tickets`. Each ticket has `id`, `ticketTypeId`, `seatId`, `attendeeEmail`, and `status`. The order ID is the checkout/order reference used by existing management endpoints. Do not assume the case requester is the purchaser.
- `payment` (nullable): the existing Payments OHS PaymentView, including status, refundStatus, reviewRequired and bank-operation status. Only the organizer and platform administrators can see these details. There are no card details or admission codes in this context.
- `activation` (nullable): `status` (`PENDING`, `ACTIVE`, `SUSPENDED`) and `payment` (PaymentView). Returned for platform administrators viewing a platform case linked to an event, if an activation exists. This is an inspection capability, not an activation override.
- `actions`: a list of `{ "action": "RESEND_TICKET", "ticketId": "..." }` objects. For order-level actions, `ticketId` is null or omitted by JSON serialization. Render only actions returned by this server response; do not reconstruct eligibility in React.
- `history`: chronological support audit entries (`id`, `caseId`, `eventId`, `occurredAt`, `actorUserId`, `action`, `details`), including `ACTION_*` entries for the operations described below.

The context is not cached. Refresh after an operation or periodically while awaiting a refund outcome. In particular, `payment=null` can mean the caller has no financial-inspection permission; use the returned actions, not that field alone, to determine controls.

## Execute an action

`POST /api/support/cases/{caseId}/actions`

Resend one existing ticket:

```json
{"action":"RESEND_TICKET","ticketId":"<ticket-uuid>"}
```

Reverse a check-in:

```json
{"action":"REVERSE_CHECK_IN","ticketId":"<ticket-uuid>","reason":"Accidental check-in"}
```

Cancel a free order or request a full paid order refund (show the order amount and confirmation dialog; do not present these as single-ticket operations):

```json
{"action":"CANCEL_FREE_ORDER","reason":"Customer requested cancellation"}
```

```json
{"action":"REQUEST_REFUND","reason":"Customer requested a refund"}
```

The financial actions require a nonblank reason (max 200 characters). Ticket actions take an optional reason of the same maximum length. Ticket IDs must belong to the linked order; if the case is linked to a specific ticket, only that ticket may be resent or have its check-in reversed. Order-level actions affect the **entire order**, including any other tickets it contains. An event-only case cannot perform order or ticket actions.

A successful request returns HTTP 200 with the refreshed context:

- `RESEND_TICKET`: the existing ticket delivery queue is requested. Delivery occurs asynchronously to the original attendee email, with the existing cooldown and retry policy. This does not issue a new ticket or report that the email has already been delivered.
- `REVERSE_CHECK_IN`: Ticket Inventory transitions the ticket back to VALID using the existing domain operation.
- `CANCEL_FREE_ORDER`: Order & Checkout cancels the free order and releases the unused tickets through Ticket Inventory.
- `REQUEST_REFUND`: Order & Checkout requests a full refund through Payments. `refundState=PENDING` is not a confirmed refund. Continue polling; `REFUNDED` or `FAILED` is the eventual outcome. The existing saga prevents admission while the refund is pending, and the existing refund-notification worker sends a receipt when confirmed. A captured payment with no issued order may also be refunded if it is not under review.

Errors use the application's problem JSON: 403 for access denial, 404 for missing resources, 409 for unavailable actions or invalid current state, 400 for invalid input, 429 for the ticket-email cooldown, and 503 for a payment-provider availability exception. The UI should refresh the context after a conflict. A repeat action is not necessarily eligible; do not retry blindly.

## Permissions and architecture

- Attendee: existing conversation and case status, no staff context or operational action endpoint.
- Organizer: inspect and operate on attendee cases for their own events; financial actions require the event organizer.
- Assigned EVENT_ADMIN: inspect attendee cases and resend tickets for the assigned event. Check-in reversal requires existing check-in permission. No financial operations or internal payment context.
- Platform admin: inspect authorized support cases and perform these operations across events. The existing platform support queue still lists platform cases and escalated attendee cases. Platform admins may inspect activation status in platform cases. Requesters cannot perform staff actions on their own cases.

Support composes the published OHS contracts and checks the case, while Ticket Inventory, Order & Checkout and Payments keep ownership of their domain operations. Existing customer-facing, organizer and check-in endpoints remain unchanged. Actions are recorded transactionally in the support audit store and projected into the existing organizer history. The event timeline shows generic action labels rather than the support agent's reason text.

Keep using the existing `POST /api/support/cases/{caseId}/escalate` endpoint for organizer escalation and the existing reply/status endpoints. No automatic resolution or conversation message is generated by an action; agents should notify the customer and resolve the case explicitly. No email-address correction, partial refunds, activation-fee refunds, forced successful payment, or account-suspension action is introduced here.

## Test

`./gradlew test --tests '*SupportResolutionServiceTest'`

Also test against a running demo: create a real order and case, verify that an organizer can resend the linked ticket; try an unrelated ticket ID; check that event staff cannot refund; request a refund as the organizer, poll until its outcome, and verify the receipt, ticket status and support history. Verify a free cancellation releases inventory. Test the platform-admin context on a linked activation case, including one that is still a draft.

