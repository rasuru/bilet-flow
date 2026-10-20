# Ticket delivery

Ticket Inventory owns ticket documents. The admission QR encodes the canonical random ticket code accepted by online check-in; it does not create a second ticket, encode payment data, or grant admission independently of server-side status checks.

## Documents and access

| Endpoint                                | Result                                           |
| --------------------------------------- | ------------------------------------------------ |
| `GET /api/tickets/{ticketId}/pdf`       | A4 PDF attachment suitable for printing.         |
| `GET /api/tickets/{ticketId}/qr`        | Black-and-white PNG of the admission QR.         |
| `POST /api/tickets/{ticketId}/delivery` | `202 Accepted`: email queued or already pending. |

These endpoints require authentication and authorize the linked owner, the attendee whose verified email matches the ticket, or the event organizer. Knowing a ticket ID alone does not grant access. Unclaimed tickets remain accessible to their verified email recipient; the existing claim endpoint links them to that account. Responses containing documents use `Cache-Control: no-store, private`.

The PDF includes event title, start/end times with an explicit timezone, venue/address, ticket type, attendee email, assigned seat label (or general admission), ticket/order references, and current status. Checkout currently collects attendee email rather than attendee name, so documents identify the attendee by that email. Bundled Liberation fonts support Kazakh and Cyrillic text without host font installation. Unsupported glyphs are replaced with `?`; exceptionally long fields are wrapped and truncated to keep the one-page layout printable.

Cancelled/refunded/pending-refund tickets and cancelled events have no admission QR in a newly generated PDF; their PNG request returns `409 Conflict`. A checked-in ticket retains its canonical code and displays its used status. Earlier copies cannot be recalled: online check-in remains authoritative and prevents duplicate or invalid admission. Downloads use current event details and status.

## Confirmation email lifecycle

Both free registration and confirmed paid checkout issue tickets through the same sale service. Each newly issued ticket creates one delivery row in the issuing database transaction. Rollback removes both ticket and delivery work; idempotent sale retries do not enqueue another email. The message confirms registration and attaches that ticket's PDF. Each message goes only to that ticket's recorded attendee email, not a caller-supplied address or a list of other attendees.

A scheduled worker claims pending work in a short transaction, loads the ticket presentation, then renders and sends outside that transaction. Successful SMTP acceptance is recorded separately. Failed sends are retried after 30 seconds with exponential backoff capped at one hour. A two-minute lease allows recovery after process failure; claim tokens prevent an old worker from acknowledging a replacement claim. SMTP connection/read/write waits are bounded.

Refund-pending tickets are deferred because a rejected refund can restore validity. Refunded, cancelled, already checked-in tickets, and cancelled events are skipped. Status is checked before preparing delivery; a concurrent cancellation can still occur after that check, and online admission validation handles it.

Delivery is **at least once**: a crash after SMTP acceptance but before acknowledgement can produce another email. Retries retain a stable Message-ID to aid deduplication, but SMTP does not guarantee exactly-once delivery. Duplicate emails contain the same ticket code and cannot create extra admissions. SMTP acceptance also does not guarantee final inbox delivery or report later bounces.

A manual delivery request is idempotent while work is pending. Once work has completed, a new request is allowed at least one minute after the previous request, with a new Message-ID. Tickets issued before this feature can be downloaded or explicitly queued through the same endpoints; deployment does not automatically email all historical ticket holders.

## Configuration and local mail

The sender uses the existing `spring.mail` SMTP settings and `jhipster.mail.from`. The development Compose services already include Mailpit: SMTP listens on `localhost:1025`, and captured messages can be inspected at `http://localhost:8025`. Production/demo environments must configure a working SMTP server; failures leave delivery work available for retry.

Optional properties:

| Property                                      | Default       | Purpose                                                                             |
| --------------------------------------------- | ------------- | ----------------------------------------------------------------------------------- |
| `biletflow.tickets.zone-id`                   | `Asia/Almaty` | Timezone printed on tickets.                                                        |
| `biletflow.tickets.delivery-enabled`          | `true`        | Enable the scheduled sender; queueing and downloads remain available when disabled. |
| `biletflow.tickets.delivery-delay-ms`         | `5000`        | Delay between worker batches (up to 20 messages per batch).                         |
| `biletflow.tickets.delivery-initial-delay-ms` | `10000`       | Delay before the first batch.                                                       |

The fonts-only dependency supplies embedded font resources; PDFBox renders the document and ZXing renders the QR.
