#!/usr/bin/env bash
# Buy one general-admission ticket, claim it, and download its PDF and QR.
# Requires bash, curl, jq, python3, a running backend, and two activated accounts.
# The event must be published, registration/sales open, and inventory available.
# ATTENDEE_EMAIL must match the buyer account's verified email.
#
# Usage (JWTs can instead be entered at the hidden prompts):
#   export ORGANIZER_JWT='...' BUYER_JWT='...'
#   EVENT_ID='...' TICKET_TYPE_ID='...' ATTENDEE_EMAIL='buyer@example.com' \
#     bash biletflow-purchase-e2e.sh
#
# For a paid event already activated, use the default ACTIVATE=0.
# For a new paid event, ACTIVATE=1 requests sandbox activation, accepting the
# sandbox terms and simulated identity/payout verification. Pay that fee first.
# Free tickets need no activation or browser payment.
# Optional: BASE_URL, OUTPUT_DIR, TIMEOUT_SECONDS=480, PAYMENT_PORT=9090.
# Each run creates a new purchase. The script tests retries within that purchase.
# CANCEL_EVENT=1 additionally cancels the fixture event and checks admission/email.
# REFUND_ORDER=1 requests a full paid refund and checks invalidation and its receipt.
# Otherwise leaves the ticket valid unless CANCEL_EVENT=1.
set -euo pipefail
umask 077

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
for tool in curl jq python3; do command -v "$tool" >/dev/null || fail "Install $tool"; done
BASE_URL="${BASE_URL:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"
: "${EVENT_ID:?Set EVENT_ID to a published event UUID}"
: "${TICKET_TYPE_ID:?Set TICKET_TYPE_ID to a general-admission ticket type UUID}"
: "${ATTENDEE_EMAIL:?Set ATTENDEE_EMAIL to the buyer account verified email}"
ACTIVATE="${ACTIVATE:-0}"
CANCEL_EVENT="${CANCEL_EVENT:-0}"
REFUND_ORDER="${REFUND_ORDER:-0}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-480}"
PAYMENT_PORT="${PAYMENT_PORT:-9090}"
[[ "$ACTIVATE" = 0 || "$ACTIVATE" = 1 ]] || fail 'ACTIVATE must be 0 or 1'
[[ "$CANCEL_EVENT" = 0 || "$CANCEL_EVENT" = 1 ]] || fail 'CANCEL_EVENT must be 0 or 1'
[[ "$REFUND_ORDER" = 0 || "$REFUND_ORDER" = 1 ]] || fail 'REFUND_ORDER must be 0 or 1'
[[ "$TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]] || fail 'TIMEOUT_SECONDS must be positive'
[[ "$PAYMENT_PORT" =~ ^[0-9]+$ ]] || fail 'PAYMENT_PORT must be numeric'
python3 - "$EVENT_ID" "$TICKET_TYPE_ID" "$PAYMENT_PORT" <<'PY'
import sys, uuid
for value in sys.argv[1:3]: uuid.UUID(value)
assert 1024 <= int(sys.argv[3]) <= 65535, 'Use an unprivileged TCP port'
PY
if [[ -z "${BUYER_JWT:-}" ]]; then
  read -r -s -p 'Buyer JWT: ' BUYER_JWT; printf '\n'
fi
if [[ ( "$ACTIVATE" = 1 || "$CANCEL_EVENT" = 1 || "$REFUND_ORDER" = 1 ) && -z "${ORGANIZER_JWT:-}" ]]; then
  read -r -s -p 'Organizer JWT: ' ORGANIZER_JWT; printf '\n'
fi
[[ -n "$BUYER_JWT" ]] || fail 'Buyer JWT is empty'

WORK="$(mktemp -d)"
SERVER_PID=''
cleanup() {
  if [[ -n "$SERVER_PID" ]]; then kill "$SERVER_PID" 2>/dev/null || true; wait "$SERVER_PID" 2>/dev/null || true; fi
  rm -rf "$WORK"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
CHECKOUT_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
OUTPUT_DIR="${OUTPUT_DIR:-./purchase-e2e-$CHECKOUT_ID}"
mkdir -p "$OUTPUT_DIR"
# Keep bearer credentials out of curl's command-line arguments and output files.
printf 'Authorization: Bearer %s\n' "$BUYER_JWT" > "$WORK/buyer.headers"
if [[ "$ACTIVATE" = 1 || "$CANCEL_EVENT" = 1 || "$REFUND_ORDER" = 1 ]]; then
  printf 'Authorization: Bearer %s\n' "$ORGANIZER_JWT" > "$WORK/organizer.headers"
fi
unset BUYER_JWT ORGANIZER_JWT

# api ROLE METHOD PATH OUTPUT EXPECTED [BODY_FILE] [IDEMPOTENCY_KEY]
# Never retries POST automatically; checkout replay uses the same explicit key.
api() {
  local role="$1" method="$2" path="$3" target="$4" expected="$5"
  local status
  local -a args=(--silent --show-error --connect-timeout 10 --max-time 30
    --request "$method" --header "@$WORK/$role.headers" --output "$target"
    --write-out '%{http_code}')
  if [[ -n "${6:-}" ]]; then args+=(--header 'Content-Type: application/json' --data-binary "@$6"); fi
  if [[ -n "${7:-}" ]]; then args+=(--header "Idempotency-Key: $7"); fi
  status="$(curl "${args[@]}" "$BASE_URL$path")" || fail "$method $path: connection failed"
  case ",$expected," in
    *",$status,"*) ;;
    *)
      printf 'HTTP %s: %s %s\n' "$status" "$method" "$path" >&2
      # Show only ProblemDetail fields, never session/auth response payloads.
      jq -r '[.title // empty, .detail // empty] | join(": ")' "$target" 2>/dev/null >&2 || true
      fail 'Unexpected HTTP response'
      ;;
  esac
}

start_server() {
  [[ -z "$SERVER_PID" ]] || return 0
  mkdir -p "$WORK/web"
  # Match the development return URL configured in .env.local.
  cat > "$WORK/web/halyk-sandbox-checkout.html" <<'HTML'
<!doctype html>
<html lang="en">
<meta charset="utf-8">
<title>BiletFlow payment return</title>
<style>body{max-width:640px;margin:64px auto;padding:0 24px;font:18px/1.6 system-ui}</style>
<h1>Back from Halyk</h1>
<p>Check the terminal running the purchase test for the confirmed payment result.</p>
<p>If this was the activation payment, the terminal will show a new link for the ticket payment.</p>
<p>This return page alone does not confirm a successful payment. Keep the test script running while it checks the bank status.</p>
</html>
HTML
  # Bind synchronously so an occupied port cannot silently serve a different page.
  python3 - "$WORK/web" "$PAYMENT_PORT" "$WORK/ready" <<'PY' >/dev/null 2>&1 &
import functools, http.server, pathlib, sys
handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=sys.argv[1])
with http.server.ThreadingHTTPServer(('127.0.0.1', int(sys.argv[2])), handler) as server:
    pathlib.Path(sys.argv[3]).touch()
    server.serve_forever()
PY
  SERVER_PID=$!
  local tries=0
  until [[ -f "$WORK/ready" ]]; do
    kill -0 "$SERVER_PID" 2>/dev/null || fail "Cannot serve payment page; choose a different PAYMENT_PORT ($PAYMENT_PORT is busy?)"
    tries=$((tries + 1)); [[ "$tries" -le 50 ]] || fail 'Payment page startup timed out'
    sleep 0.1
  done
}

payment_page() {
  local role="$1" payment_id="$2" phase="$3"
  start_server
  api "$role" POST "/api/payments/$payment_id/session" "$WORK/session.json" 200
  # A bank session contains temporary credentials: serve only on loopback,
  # never copy it to OUTPUT_DIR, and remove it when this process exits.
  python3 - "$WORK/session.json" "$WORK/web/$phase.html" <<'PY'
import json, pathlib, sys
session = json.loads(pathlib.Path(sys.argv[1]).read_text())
script = 'https://test-epay.epayment.kz/payform/payment-api.js'
if session.get('scriptUrl') != script:
    raise SystemExit('Refusing an unexpected/non-sandbox payment script')
payment = json.dumps(session['paymentObject']).replace('<', '\\u003c')
page = '''<!doctype html><meta charset="utf-8"><title>BiletFlow test payment</title>
<h1>BiletFlow Halyk sandbox</h1><p>Use sandbox card details. Keep the terminal script running.</p>
<button id="pay" disabled>Open Halyk test payment</button><p id="message">Loading bank form…</p>
<script src="''' + script + '''" onload="document.getElementById('pay').disabled=false;document.getElementById('message').textContent='Ready'"
onerror="document.getElementById('message').textContent='Bank script failed to load'"></script>
<script>const payment = ''' + payment + ''';
document.getElementById('pay').onclick = () => { halyk.pay(payment); };</script>'''
pathlib.Path(sys.argv[2]).write_text(page)
PY
  printf '\nOpen http://127.0.0.1:%s/%s.html and complete the Halyk sandbox payment.\n' "$PAYMENT_PORT" "$phase"
  printf 'Waiting for backend confirmation (up to %ss)…\n' "$TIMEOUT_SECONDS"
}

if [[ "$ACTIVATE" = 1 ]]; then
  printf '%s\n' '{"acceptSandboxTerms":true,"simulateIdentityAndPayoutVerification":true}' > "$WORK/activation-request.json"
  api organizer POST "/api/payments/events/$EVENT_ID/activation" "$WORK/activation.json" 200 "$WORK/activation-request.json"
  api organizer GET "/api/payments/events/$EVENT_ID/activation" "$WORK/activation-status.json" 200
  if [[ "$(jq -r '.status' "$WORK/activation-status.json")" != ACTIVE ]]; then
    activation_payment="$(jq -er '.paymentId' "$WORK/activation.json")"
    payment_page organizer "$activation_payment" activation
    deadline=$((SECONDS + TIMEOUT_SECONDS))
    while :; do
      api organizer GET "/api/payments/events/$EVENT_ID/activation" "$WORK/activation-status.json" 200
      [[ "$(jq -r '.status' "$WORK/activation-status.json")" != ACTIVE ]] || break
      [[ "$SECONDS" -lt "$deadline" ]] || fail 'Activation did not become ACTIVE; inspect payment status before retrying'
      sleep 3
    done
    rm -f "$WORK/web/activation.html"
  fi
  printf 'PASS: paid-sales activation is ACTIVE\n'
fi

jq -n --arg event "$EVENT_ID" --arg type "$TICKET_TYPE_ID" --arg email "$ATTENDEE_EMAIL" \
  '{eventId:$event,selections:[{ticketTypeId:$type,seats:[],attendeeEmails:[$email]}]}' > "$WORK/request.json"
printf 'Checkout ID: %s\n' "$CHECKOUT_ID"
api buyer POST /api/checkouts "$WORK/checkout.json" 200 "$WORK/request.json" "$CHECKOUT_ID"
jq -e --arg id "$CHECKOUT_ID" --arg event "$EVENT_ID" \
  '.id == $id and .eventId == $event and (.status == "AWAITING_PAYMENT" or .status == "COMPLETED")' "$WORK/checkout.json" >/dev/null || fail 'Unexpected checkout'
api buyer POST /api/checkouts "$WORK/replay.json" 200 "$WORK/request.json" "$CHECKOUT_ID"
[[ "$(jq -c '[.id,.paymentId,.amountKzt]' "$WORK/checkout.json")" = "$(jq -c '[.id,.paymentId,.amountKzt]' "$WORK/replay.json")" ]] || fail 'Checkout retry changed purchase identity/price'
printf 'PASS: checkout retry reused the same purchase and price\n'

if [[ "$REFUND_ORDER" = 1 ]]; then
  jq -e '.amountKzt >= 10' "$WORK/checkout.json" >/dev/null || fail 'REFUND_ORDER requires a paid ticket of at least 10 KZT'
fi
if [[ "$(jq -r '.status' "$WORK/checkout.json")" = AWAITING_PAYMENT ]]; then
  payment_id="$(jq -er '.paymentId' "$WORK/checkout.json")"
  payment_page buyer "$payment_id" checkout
  deadline=$((SECONDS + TIMEOUT_SECONDS))
  while :; do
    api buyer GET "/api/checkouts/$CHECKOUT_ID" "$WORK/checkout.json" 200
    status="$(jq -r '.status' "$WORK/checkout.json")"
    [[ "$status" != COMPLETED ]] || break
    [[ "$status" = AWAITING_PAYMENT ]] || fail "Checkout ended with status $status"
    [[ "$SECONDS" -lt "$deadline" ]] || fail "Checkout timed out. Inspect $CHECKOUT_ID before starting another purchase"
    sleep 3
  done
  rm -f "$WORK/web/checkout.html"
fi
jq -e --arg type "$TICKET_TYPE_ID" --arg email "$ATTENDEE_EMAIL" \
  '.status == "COMPLETED" and (.tickets | length) == 1 and .tickets[0].status == "VALID" and .tickets[0].ticketTypeId == $type and (.tickets[0].attendeeEmail | ascii_downcase) == ($email | ascii_downcase)' \
  "$WORK/checkout.json" >/dev/null || fail 'Expected exactly one valid ticket for the buyer'
ticket_id="$(jq -er '.tickets[0].id' "$WORK/checkout.json")"
api buyer POST /api/checkouts "$WORK/replay.json" 200 "$WORK/request.json" "$CHECKOUT_ID"
[[ "$(jq -c '.tickets | map(.id) | sort' "$WORK/checkout.json")" = "$(jq -c '.tickets | map(.id) | sort' "$WORK/replay.json")" ]] || fail 'Replay issued different tickets'
printf 'PASS: purchase completed and replay returned the same ticket\n'

api buyer POST "/api/tickets/$ticket_id/claim" "$WORK/claim.json" 200,204
api buyer POST "/api/tickets/$ticket_id/claim" "$WORK/claim-replay.json" 200,204
api buyer GET "/api/tickets/$ticket_id/pdf" "$OUTPUT_DIR/ticket-$ticket_id.pdf" 200
api buyer GET "/api/tickets/$ticket_id/qr" "$OUTPUT_DIR/ticket-$ticket_id.png" 200
python3 - "$OUTPUT_DIR/ticket-$ticket_id.pdf" "$OUTPUT_DIR/ticket-$ticket_id.png" <<'PY'
import pathlib, sys
assert pathlib.Path(sys.argv[1]).read_bytes().startswith(b'%PDF-'), 'Invalid PDF response'
assert pathlib.Path(sys.argv[2]).read_bytes().startswith(b'\x89PNG\r\n\x1a\n'), 'Invalid PNG response'
PY
cp "$WORK/checkout.json" "$OUTPUT_DIR/checkout.json"
printf 'PASS: verified-email claim is repeatable; ticket PDF and QR downloaded\n'
printf '\nPurchase passed. Ticket: %s\nFiles: %s\n' "$ticket_id" "$OUTPUT_DIR"
printf 'Automatic email delivery is asynchronous; inspect the attendee inbox (Mailpit in dev).\n'
if [[ "$CANCEL_EVENT" = 1 ]]; then
  printf '\nTesting event cancellation…\n'
  api organizer POST "/api/events/$EVENT_ID/cancel" "$WORK/cancel.json" 204
  api organizer POST "/api/events/$EVENT_ID/cancel" "$WORK/cancel-replay.json" 204
  api buyer GET "/api/events/$EVENT_ID" "$WORK/cancelled-event.json" 200
  jq -e '.status == "CANCELLED"' "$WORK/cancelled-event.json" >/dev/null || fail 'Event is not cancelled'
  api buyer GET "/api/tickets/$ticket_id/qr" "$WORK/rejected-qr.json" 409
  api buyer GET "/api/tickets/$ticket_id/pdf" "$OUTPUT_DIR/ticket-$ticket_id-cancelled.pdf" 200
  rejected_key="$(python3 -c 'import uuid; print(uuid.uuid4())')"
  api buyer POST /api/checkouts "$WORK/rejected-checkout.json" 409 "$WORK/request.json" "$rejected_key"
  ticket_code="$(jq -er '.tickets[0].code' "$WORK/checkout.json")"
  api organizer POST "/api/events/$EVENT_ID/check-in/tickets/$ticket_code" "$WORK/rejected-admission.json" 403
  api buyer GET "/api/checkouts/$CHECKOUT_ID" "$WORK/retained-order.json" 200
  jq -e '.status == "COMPLETED" and .refundId == null' "$WORK/retained-order.json" >/dev/null || fail 'Cancellation altered the completed financial order'
  printf 'PASS: repeated cancellation is safe; sales, QR download, and admission are blocked; completed order retained\n'
  printf 'Waiting for the attendee cancellation notice in Mailpit…\n'
  mail_base="${MAILPIT_URL:-http://localhost:8025}"
  deadline=$((SECONDS + 60))
  while :; do
    mail_status="$(curl --silent --show-error --connect-timeout 5 --max-time 10 --get \
      --data-urlencode "query=to:$ATTENDEE_EMAIL subject:\"BiletFlow event cancelled:\"" \
      --output "$WORK/cancellation-email.txt" --write-out '%{http_code}' "${mail_base%/}/view/latest.txt")" || fail 'Cannot reach Mailpit'
    if [[ "$mail_status" = 200 ]]; then
      cp "$WORK/cancellation-email.txt" "$OUTPUT_DIR/cancellation-email.txt"
      break
    fi
    [[ "$mail_status" = 404 ]] || fail "Mailpit returned HTTP $mail_status"
    [[ "$SECONDS" -lt "$deadline" ]] || fail 'Cancellation email did not arrive within 60s; check outbox, cancellation queue, and SMTP logs'
    sleep 2
  done
  printf 'PASS: attendee cancellation email arrived in Mailpit\n'
elif [[ "$REFUND_ORDER" = 0 ]]; then
  printf 'This script does not assert SMTP delivery or admission scanning. Use CANCEL_EVENT=1 or REFUND_ORDER=1 for their email checks.\n'
fi

if [[ "$REFUND_ORDER" = 1 ]]; then
  printf '\nTesting the paid refund…\n'
  printf '%s\n' '{"reason":"E2E full refund"}' > "$WORK/refund-request.json"
  api buyer POST "/api/checkouts/$CHECKOUT_ID/refund" "$WORK/unauthorized-refund.json" 403 "$WORK/refund-request.json"
  api organizer POST "/api/checkouts/$CHECKOUT_ID/refund" "$WORK/refund.json" 200 "$WORK/refund-request.json"
  refund_id="$(jq -er '.refundId' "$WORK/refund.json")"
  api organizer POST "/api/checkouts/$CHECKOUT_ID/refund" "$WORK/refund-replay.json" 200 "$WORK/refund-request.json"
  [[ "$(jq -er '.refundId' "$WORK/refund-replay.json")" = "$refund_id" ]] || fail 'Retry changed refund identity'
  api buyer GET "/api/tickets/$ticket_id/qr" "$WORK/pending-refund-qr.json" 409
  ticket_code="$(jq -er '.tickets[0].code' "$WORK/checkout.json")"
  admission_status=409
  [[ "$CANCEL_EVENT" = 0 ]] || admission_status=403
  api organizer POST "/api/events/$EVENT_ID/check-in/tickets/$ticket_code" "$WORK/pending-refund-admission.json" "$admission_status"
  printf 'Waiting for confirmed refund (up to %ss); payment %s, refund %s…\n' "$TIMEOUT_SECONDS" "$payment_id" "$refund_id"
  deadline=$((SECONDS + TIMEOUT_SECONDS))
  next_progress=$SECONDS
  while :; do
    api buyer GET "/api/checkouts/$CHECKOUT_ID" "$WORK/refunded-checkout.json" 200
    refund_state="$(jq -r '.refundState' "$WORK/refunded-checkout.json")"
    [[ "$refund_state" != REFUNDED ]] || break
    [[ "$refund_state" = PENDING ]] || fail "Refund ended with state $refund_state"
    if [[ "$SECONDS" -ge "$next_progress" || "$SECONDS" -ge "$deadline" ]]; then
      api buyer GET "/api/payments/$payment_id" "$WORK/refund-payment-status.json" 200
      jq '{id,orderId,status,refundId,refundStatus,reviewRequired,bankOperation,bankOperationId,bankOperationState,sandbox}' \
        "$WORK/refund-payment-status.json" > "$OUTPUT_DIR/refund-payment-status.json"
      jq -r '"Payment: \(.status); refund: \(.refundStatus); operation: \(.bankOperationState); review required: \(.reviewRequired)"' \
        "$OUTPUT_DIR/refund-payment-status.json"
      next_progress=$((SECONDS + 30))
    fi
    [[ "$SECONDS" -lt "$deadline" ]] || fail "Refund remains unresolved. State saved to $OUTPUT_DIR/refund-payment-status.json; inspect payment $payment_id before retrying"
    sleep 3
  done
  jq -e --arg refund "$refund_id" '.status == "CANCELLED" and .refundState == "REFUNDED" and .refundId == $refund and (.tickets | length) == 1 and .tickets[0].status == "REFUNDED"'     "$WORK/refunded-checkout.json" >/dev/null || fail 'Refund did not invalidate the ticket'
  api buyer GET "/api/payments/$payment_id" "$WORK/refunded-payment.json" 200
  jq -e --arg refund "$refund_id" '.status == "REFUNDED" and .refundId == $refund and .sandbox == true'     "$WORK/refunded-payment.json" >/dev/null || fail 'Payment does not confirm the sandbox refund'
  api buyer GET "/api/tickets/$ticket_id/qr" "$WORK/refunded-qr.json" 409
  api buyer GET "/api/tickets/$ticket_id/pdf" "$OUTPUT_DIR/ticket-$ticket_id-refunded.pdf" 200
  api organizer POST "/api/events/$EVENT_ID/check-in/tickets/$ticket_code" "$WORK/refunded-admission.json" "$admission_status"
  api organizer POST "/api/checkouts/$CHECKOUT_ID/refund" "$WORK/completed-refund-replay.json" 200 "$WORK/refund-request.json"
  [[ "$(jq -er '.refundId' "$WORK/completed-refund-replay.json")" = "$refund_id" ]] || fail 'Completed refund retry changed identity'
  cp "$WORK/refunded-checkout.json" "$OUTPUT_DIR/refunded-checkout.json"
  printf 'PASS: refund is confirmed, repeat requests reuse its identity, and the ticket is invalidated\n'
  printf 'Waiting for the purchaser refund receipt in Mailpit…\n'
  mail_base="${MAILPIT_URL:-http://localhost:8025}"
  deadline=$((SECONDS + 60))
  while :; do
    mail_status="$(curl --silent --show-error --connect-timeout 5 --max-time 10 --get       --data-urlencode "query=to:$ATTENDEE_EMAIL subject:\"BiletFlow refund confirmed (sandbox)\""       --output "$WORK/refund-email.txt" --write-out '%{http_code}' "${mail_base%/}/view/latest.txt")" || fail 'Cannot reach Mailpit'
    if [[ "$mail_status" = 200 ]]; then
      if python3 - "$WORK/refund-email.txt" "$CHECKOUT_ID" "$refund_id" "$WORK/refunded-checkout.json" <<'CHECK_RECEIPT'
from pathlib import Path
from decimal import Decimal
import json, sys
text=Path(sys.argv[1]).read_text()
assert sys.argv[2] in text and sys.argv[3] in text, 'Receipt must match this order and refund'
assert 'No real money' in text, 'Receipt must identify the sandbox'
checkout=json.loads(Path(sys.argv[4]).read_text())
amount=Decimal(str(checkout['amountKzt'])).quantize(Decimal('0.01'))
assert f'Amount: {amount} KZT' in text, 'Receipt must match the full purchase amount'
CHECK_RECEIPT
      then
        cp "$WORK/refund-email.txt" "$OUTPUT_DIR/refund-email.txt"
        break
      fi
    else
      [[ "$mail_status" = 404 ]] || fail "Mailpit returned HTTP $mail_status"
    fi
    [[ "$SECONDS" -lt "$deadline" ]] || fail 'Refund receipt did not arrive within 60s; inspect refund queue and SMTP logs'
    sleep 2
  done
  printf 'PASS: purchaser receipt arrived in Mailpit with matching order and refund references\n'
fi

