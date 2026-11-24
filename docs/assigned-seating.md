# Predefined assigned seating

Event Management owns layout reference data. Ticket Inventory owns event-specific seat allocation. Four immutable templates are seeded by Liquibase; their UUIDs and seat UUIDs are stable across installations. Do not change the identity or physical location of a seat already referenced by an event.

## Organizer flow

1. `GET /api/venue-layouts` returns full layouts. `GET /api/venue-layouts/{layoutId}` returns one. Both already existed; the response now includes `width`, `height`, `focus` (nullable rectangle with label), and per-seat `x`, `y` (center coordinates). The coordinate space is 800 × 600. Older layouts without geometry have null coordinates and focus; clients can fall back to a row-based list/grid.
2. Create the draft using the existing event API, then `POST /api/events/{eventId}/venue` with `name`, `address`, `capacity` equal to `seats.length`, `seatingMode: "ASSIGNED_SEATING"`, and `venueLayoutId: layout.layoutId`. Venue attachment is immutable. Use full layout capacity: inventory currently initializes all template seats, not a subset.
3. Create assigned-seating ticket types using `POST /api/events/{eventId}/ticket-types/assigned-seating` and a `priceCategory` matching a category in the layout (`PREMIUM`, `STANDARD`, `ECONOMY`). Prices belong to ticket types, not templates. Categories without a purchasable ticket type must not be selectable.

## Attendee flow

- `GET /api/events/{eventId}` returns `venue.venueLayoutId` for assigned seating.
- Fetch `GET /api/venue-layouts/{layoutId}` for geometry and `GET /api/events/{eventId}/seat-map` for event inventory (`[{"seatId":"…","status":"AVAILABLE"}]`). Join by seatId, not by position or array index. The availability endpoint reports `AVAILABLE`, `HELD`, and `SOLD`; an expired hold is available even before the next cleanup job. It does not represent whether sales are currently permitted. A missing inventory (404) means there is no initialized ticket inventory; do not treat all seats as available.
- Fetch `GET /api/events/{eventId}/ticket-types` for actual prices, category, sales windows and ticket type status. A seat without an eligible ticket type or with missing inventory should be rendered unavailable. `SELECTED` is local UI state. The public availability endpoint does not identify hold owners. Refresh availability periodically, and rely on checkout for authoritative validation.
- Layout `focus` is a rectangle for stage/arena; banquet tables can be drawn at the average center of seats sharing a section.

No new reservation endpoint or bounded context is needed. Existing checkout and ticket inventory continue to use seat UUIDs.

