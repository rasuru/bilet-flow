# Promotional campaigns

Set `BILETFLOW_FRONTEND` to the web app origin in `.env.local` for local runs or in the backend deployment environment. It defaults to `http://localhost:5173`. Use your HTTPS origin for deployment. Campaign QR links and account activation, creation and password-reset emails all use this origin.

## Organizer API

- Create and list campaigns at `/api/events/{eventId}/campaigns`.
- Read a campaign at `GET /api/campaigns/{campaignId}` or end it with `POST /api/campaigns/{campaignId}/end`.
- Enable or disable a code with `POST /api/promo-codes/{promoCodeId}/enable` or `/disable`.
- Download the campaign QR PNG at `GET /api/campaigns/{campaignId}/promo-codes/{promoCodeId}/qr`. It opens `/events/{eventId}?promo={campaignToken}` and is separate from the QR used for admission.

## Attendee web integration

1. Read `promo` from the event URL and call `GET /api/campaign-links/{campaignToken}`. Check the returned event ID matches the event page and preserve the returned `code` through navigation to checkout. Attendees can also enter a promo code manually.
2. Call `POST /api/checkouts/quote` with `eventId`, `promoCode`, and `selections` such as `[{"ticketTypeId":"…","quantity":2}]`. Use `subtotalKzt`, `discountKzt`, and `amountKzt` to show the breakdown. Quotes are public for publicly readable events and make no reservations.
3. Submit the promo code with the normal authenticated `POST /api/checkouts` request and its `Idempotency-Key`. Retry with the same key and input. Use a new key when the code or basket changes. Show the `detail` from a promo rejection and let the attendee change or remove the code.
4. The backend reserves a redemption until the inventory hold deadline, confirms it when the tickets and order are created, and releases it if checkout fails, expires, or is cancelled before fulfillment. A fully discounted purchase of originally paid tickets still requires paid-sales activation.

Campaign totals describe eligible tickets and sales before refunds. Confirmed redemptions remain in campaign history.

## Database

Start the server normally to apply the pending Liquibase changes. They add the current redemption table and checkout references and retire the unused legacy redemption table. You can restart with your existing database.
