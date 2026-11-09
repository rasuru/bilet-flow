## REST API errors

REST errors use `application/problem+json`. Use the HTTP status for generic handling and the exact `type` URI for behavior specific to a problem. Fall back to the status for unknown types, `about:blank`, or an error response without a JSON body. Treat `title` and `detail` as diagnostic text.

`fieldErrors` is used for request validation. Each entry has a `field` path and a diagnostic `message`. When available, `code` names the validation rule, such as `NotNull` or `Email`. The legacy `objectName` remains available. Paths use dots for nested properties and brackets for list indexes, such as `selections[0].attendeeEmails[1]`. Multiple errors can refer to the same field. Validation that has no specific field can return an empty array.

Business problem types can define their own structured properties. Clients should tolerate additional properties. Legacy IAM `message`, `params`, and `errorKey` values may still be present. New client code should use `type` for specific handling.

### Existing problem types

| Exact type URI | Status | When it occurs |
| --- | --- | --- |
| `about:blank` | Varies | Generic error whose status is enough to select a fallback. |
| `https://www.jhipster.tech/problem/constraint-violation` | 400 | Request validation failed. See `fieldErrors`. |
| `https://www.jhipster.tech/problem/email-already-used` | 400 | The email address is already in use. |
| `https://www.jhipster.tech/problem/login-already-used` | 400 | The login is already in use. |
| `https://www.jhipster.tech/problem/invalid-password` | 400 | The supplied current password was rejected. |
| `https://www.jhipster.tech/problem/problem-with-message` | 400 | Legacy generic IAM request rejection. Use the HTTP status as a fallback. |

### malformed-request

- **Type** `https://github.com/rasuru/bilet-flow/blob/main/docs/api-errors.md#malformed-request`
- **400** The server could not read the request body as the expected input.

### invalid-credentials

- **Type** `https://github.com/rasuru/bilet-flow/blob/main/docs/api-errors.md#invalid-credentials`
- **401** Sign-in credentials were rejected.

### account-not-activated

- **Type** `https://github.com/rasuru/bilet-flow/blob/main/docs/api-errors.md#account-not-activated`
- **401** The account needs email activation before sign-in.

### invalid-activation-key

- **Type** `https://github.com/rasuru/bilet-flow/blob/main/docs/api-errors.md#invalid-activation-key`
- **400** The key no longer identifies an account awaiting activation.

### invalid-password-reset-key

- **Type** `https://github.com/rasuru/bilet-flow/blob/main/docs/api-errors.md#invalid-password-reset-key`
- **400** The key cannot complete a password reset.

### checkout-idempotency-conflict

- **Type** `https://github.com/rasuru/bilet-flow/blob/main/docs/api-errors.md#checkout-idempotency-conflict`
- **409** The checkout key was already used with different input. A retry of the same purchase must reuse the original key and original input.

### registration-closed

- **Type** `https://github.com/rasuru/bilet-flow/blob/main/docs/api-errors.md#registration-closed`
- **409** The event is no longer accepting registrations.

### mixed-seating-modes

- **Type** `https://github.com/rasuru/bilet-flow/blob/main/docs/api-errors.md#mixed-seating-modes`
- **400** Checkout selections combine assigned seating and general admission.

### inventory-unavailable

- **Type** `https://github.com/rasuru/bilet-flow/blob/main/docs/api-errors.md#inventory-unavailable`
- **409** The server could not reserve the requested inventory. Refresh availability before changing the selection.
- **Optional `unavailableSeatIds`** An array of seat UUIDs known to be unavailable. It can contain only the first seat that failed, so it is not a complete availability report. General admission failures omit it.

### payment-provider-unavailable

- **Type** `https://github.com/rasuru/bilet-flow/blob/main/docs/api-errors.md#payment-provider-unavailable`
- **503** The payment provider is temporarily unavailable.

### ticket-not-found

- **Type** `https://github.com/rasuru/bilet-flow/blob/main/docs/api-errors.md#ticket-not-found`
- **404** No ticket matches the supplied ticket ID or ticket code.

### ticket-check-in-state-conflict

- **Type** `https://github.com/rasuru/bilet-flow/blob/main/docs/api-errors.md#ticket-check-in-state-conflict`
- **409** The ticket's current state prevents checking it in or reversing its check-in.
- **`ticketStatus`** The current ticket status, such as `CHECKED_IN`, `CANCELLED`, or `REFUND_PENDING`.

### concurrent-update

- **Type** `https://github.com/rasuru/bilet-flow/blob/main/docs/api-errors.md#concurrent-update`
- **409** The operation conflicted with a concurrent update.
