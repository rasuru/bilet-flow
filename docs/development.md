# Local development

Use Java 21 (or the supported Java 25 toolchain), Docker with Docker Compose,
Bash, and OpenSSL. Run the following from the repository root; the same commands
work from Bash, fish, and zsh.

## First setup

```sh
bash dev.sh init
```

Edit `.env.local` and enter the Halyk sandbox client ID, client secret, and
terminal ID. Credentials are available through Halyk's sandbox onboarding;
see [the payment guide](payment-saga.md). The backend can run without these
credentials, but paid payment sessions will be unavailable.

`.env.local` is Git-ignored and created with owner-only permissions. It uses
Bash assignment syntax. Quote values containing shell characters; for example,
`VARIABLE='a value with $ and # characters'`. The launcher sources this file,
so it must contain only trusted local configuration. Its values override matching
variables inherited from the terminal for the launched process.

Initialization generates the payment session encryption key once and retains
it on subsequent runs. Keep the key while retaining the development database:
changing it prevents decryption of previously stored Halyk sessions. Do not
commit `.env.local` or copy its secrets into `.env.example`.

## Run

```sh
bash dev.sh
```

The launcher loads `.env.local`, starts the existing PostgreSQL, Redis,
Elasticsearch, and Mailpit services with Docker Compose, and runs Gradle
`bootRun` with the project's development profile and a plain console. It can
be invoked from another directory using its full path. If `.env.local` does
not exist, the launcher initializes it automatically.

- Backend: <http://localhost:8080>
- Development email inbox: <http://localhost:8025>

Changes to `.env.local` take effect after restarting the backend. Ctrl+C stops
the backend and leaves dependencies running for the next launch. To stop those
containers while preserving their database data:

```sh
bash dev.sh stop
```

To start dependencies without the backend:

```sh
bash dev.sh services
```

Use `stop` rather than removing containers when keeping local data. The
repository's PostgreSQL Compose configuration stores its database inside the
container unless an explicit persistent volume is configured.

## Paid sales without event activation

Set `BILETFLOW_DEV_BYPASS_PAID_SALES_ACTIVATION=true` in `.env.local`
and restart the backend to sell paid tickets without an activation fee.
This setting applies only to the Spring `dev` profile and is disabled
when `prod` is active. Suspended paid sales still block new checkouts.
Paid ticket checkouts still require working Halyk sandbox credentials.

## BiletFlow mock checkout

A platform administrator can choose **BiletFlow mock checkout** in **Admin → Platform settings → Payment provider**. The setting applies to new ticket and activation payments. Existing payments and refunds stay on the provider selected when the payment was created. Halyk sandbox is the default.

Continue to payment from an order or the organizer activation checklist. The mock page can succeed immediately, fail, expire immediately, or succeed after 10 seconds. The scheduled worker completes delayed payments even after the page closes. Use the order's refund button when eligible, or the existing support and admin refund actions, to test refunds. The mock settlement requires neither Halyk credentials nor card details.

## Halyk browser testing

In another terminal, serve the existing payment helper:

```sh
python3 -m http.server 9090 --bind 127.0.0.1 --directory docs
```

Open <http://localhost:9090/halyk-sandbox-checkout.html>. The default payment
return URL points to this helper. Localhost callbacks are not reachable from
Halyk; scheduled bank-status reconciliation still confirms local payments.
For callback testing, set `BILETFLOW_PAYMENTS_PUBLIC_BASE_URL` in `.env.local`
to the externally reachable HTTPS origin forwarding to the backend.

The purchase smoke-test script serves its own temporary payment page on port
9090. Stop the documentation server before using that script, or set a different
`PAYMENT_PORT` for the script.

## Integration tests

```sh
./gradlew integrationTest
```

Integration tests use the project's test profiles and Testcontainers. They do
not need Halyk credentials or `.env.local`; the development launcher does not
change the invoking terminal's environment.

