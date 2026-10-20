# Halyk sandbox invoice identifiers

Halyk requires a unique numeric invoice ID of 6–15 digits. Its payment-page
contract also requires uniqueness of the final six digits:
<https://epayment.kz/docs/platezhnaya-stranica>.

The public test terminal is shared. A database sequence starting at `100000`
can therefore collide with invoices submitted by other developers. Error
`3013` / `Dublicate order request` means the bank has already seen that order
number; changing the card does not resolve the collision.

New payments reserve randomized six-digit bank invoice numbers in
`pm_halyk_invoice_allocation`. The existing sequence identifies allocation
rows internally. A transaction lock serializes reservation across application
instances sharing the same database; a unique constraint prevents local reuse.
Earlier allocation IDs and payment records remain reserved, and existing
payments and encrypted forms retain their original bank invoice IDs.

Randomization avoids the predictable initial collisions of independent local
databases. It cannot guarantee uniqueness against other users of the public
terminal. A dedicated sandbox terminal is needed for an isolated merchant
namespace. Never treat a duplicate response as successful payment, or replace
the invoice on an existing payment whose bank outcome is unresolved.

A previously opened payment form retains its original invoice number after
restarting the backend. Test new invoice allocation with a fresh payment;
the end-to-end setup script creates fresh test events and payments.

