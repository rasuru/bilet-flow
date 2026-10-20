#!/usr/bin/env bash
# Create fresh fixtures via public APIs, activate accounts through Mailpit,
# then run biletflow-purchase-e2e.sh from the same directory.
# Requires a running dev backend + Mailpit, curl, jq, Python 3, and Bash.
#   bash biletflow-run-e2e.sh              # Halyk activation + paid purchase
#   PRICE_KZT=0 bash biletflow-run-e2e.sh  # fully automated free purchase
# Options: BASE_URL, MAILPIT_URL, PRICE_KZT (0 or >=10, max 2 decimals),
# MAIL_TIMEOUT_SECONDS=60, E2E_SCRIPT, OUTPUT_DIR, PAYMENT_PORT, TIMEOUT_SECONDS.
# CANCEL_EVENT=1 runs cancellation checks after purchase (also works with PRICE_KZT=0).
# REFUND_ORDER=1 runs the paid refund and receipt checks after purchase.
# Each run creates two accounts and an UNLISTED published event. Fixtures remain
# for inspection; no SQL, admin seed account, inbox deletion, or auth bypass.
set -euo pipefail
umask 077
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
for tool in curl jq python3; do command -v "$tool" >/dev/null || fail "Install $tool"; done
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
E2E_SCRIPT="${E2E_SCRIPT:-$ROOT/biletflow-purchase-e2e.sh}"
[[ -f "$E2E_SCRIPT" ]] || fail "Put biletflow-purchase-e2e.sh next to this script, or set E2E_SCRIPT to its path"
BASE_URL="${BASE_URL:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"
MAILPIT_URL="${MAILPIT_URL:-http://localhost:8025}"
MAILPIT_URL="${MAILPIT_URL%/}"
PRICE_KZT="${PRICE_KZT:-1000}"
MAIL_TIMEOUT_SECONDS="${MAIL_TIMEOUT_SECONDS:-60}"
REFUND_ORDER="${REFUND_ORDER:-0}"
[[ "$REFUND_ORDER" = 0 || "$REFUND_ORDER" = 1 ]] || fail 'REFUND_ORDER must be 0 or 1'
if [[ "$REFUND_ORDER" = 1 ]]; then
  python3 - "$PRICE_KZT" <<'CHECK_REFUND_PRICE'
from decimal import Decimal
import sys
if Decimal(sys.argv[1]) < 10: raise SystemExit('REFUND_ORDER requires PRICE_KZT >= 10')
CHECK_REFUND_PRICE
fi
[[ "$MAIL_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]] || fail 'MAIL_TIMEOUT_SECONDS must be positive'
python3 - "$PRICE_KZT" <<'PY'
from decimal import Decimal
import re, sys
s=sys.argv[1]
if not re.fullmatch(r'\d+(?:\.\d{1,2})?',s): raise SystemExit('PRICE_KZT must be a nonnegative amount with at most 2 decimals')
p=Decimal(s)
if p != 0 and p < 10: raise SystemExit('Paid checkout must be at least 10 KZT')
PY
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
RUN_ID="$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
PASSWORD="$(python3 -c 'import secrets; print(secrets.token_urlsafe(24))')"

# request ROLE METHOD PATH TARGET EXPECTED [BODY_FILE]
# ROLE is public or organizer. JWTs stay in a private temporary header file.
request() {
    local role="$1" method="$2" path="$3" target="$4" expected="$5" code
    local -a args=(--silent --show-error --connect-timeout 10 --max-time 30
        --request "$method" --output "$target" --write-out '%{http_code}')
    if [[ "$role" != public ]]; then args+=(--header "@$WORK/$role.headers"); fi
    if [[ -n "${6:-}" ]]; then args+=(--header 'Content-Type: application/json' --data-binary "@$6"); fi
    code="$(curl "${args[@]}" "$BASE_URL$path")" || fail "Cannot reach $BASE_URL ($method $path)"
    [[ "$code" = "$expected" ]] || {
        printf 'HTTP %s: %s %s\n' "$code" "$method" "$path" >&2
        jq -r '[.title // empty, .detail // empty] | join(": ")' "$target" 2>/dev/null >&2 || true
        fail 'Unexpected API response'
    }
}
request public GET /api/events "$WORK/events.json" 200
code="$(curl --silent --show-error --connect-timeout 5 --max-time 10 --output /dev/null --write-out '%{http_code}' "$MAILPIT_URL/")" || fail 'Start Mailpit (bash dev.sh services)'
[[ "$code" = 200 ]] || fail "Mailpit is unavailable at $MAILPIT_URL (HTTP $code)"

create_account() {
    local role="$1" login="e2e-$1-$RUN_ID" email="e2e-$1-$RUN_ID@example.com"
    jq -n --arg login "$login" --arg email "$email" --arg password "$PASSWORD" \
        '{login:$login,email:$email,password:$password,firstName:"E2E",lastName:"Test",langKey:"en"}' > "$WORK/register.json"
    request public POST /api/register "$WORK/register-response.json" 201 "$WORK/register.json"
    local deadline=$((SECONDS + MAIL_TIMEOUT_SECONDS)) code key
    while :; do
        # Official Mailpit latest-message HTML endpoint, filtered to this unique recipient.
        code="$(curl --silent --show-error --connect-timeout 5 --max-time 10 --get \
            --data-urlencode "query=to:$email" --output "$WORK/activation.html" --write-out '%{http_code}' \
            "$MAILPIT_URL/view/latest.html")" || fail 'Mailpit connection failed'
        case "$code" in
            200) break ;;
            404) ;;
            *) fail "Mailpit returned HTTP $code while waiting for $role activation email" ;;
        esac
        [[ "$SECONDS" -lt "$deadline" ]] || fail "No activation email for $email. Check backend SMTP settings (localhost:1025) and Mailpit"
        sleep 1
    done
    key="$(python3 - "$WORK/activation.html" <<'PY'
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlparse, parse_qs
import sys
class Activation(HTMLParser):
    key=None
    def handle_starttag(self,tag,attrs):
        if tag != 'a': return
        link=dict(attrs).get('href','')
        parsed=urlparse(link)
        if parsed.path.endswith('/account/activate'):
            values=parse_qs(parsed.query).get('key',[])
            if values: self.key=values[0]
p=Activation(); p.feed(Path(sys.argv[1]).read_text())
if not p.key: raise SystemExit('Activation email has no account activation link')
print(p.key)
PY
)"
    # Call only the configured backend, never an arbitrary URL from the email.
    code="$(curl --silent --show-error --connect-timeout 10 --max-time 30 --get \
        --data-urlencode "key=$key" --output "$WORK/activate-response.json" --write-out '%{http_code}' \
        "$BASE_URL/api/activate")" || fail 'Account activation connection failed'
    [[ "$code" = 200 ]] || fail "Account activation returned HTTP $code"
    jq -n --arg user "$login" --arg password "$PASSWORD" \
        '{username:$user,password:$password,rememberMe:false}' > "$WORK/login.json"
    request public POST /api/authenticate "$WORK/login-response.json" 200 "$WORK/login.json"
    local jwt
    jwt="$(jq -er '.id_token | select(type == "string" and length > 0)' "$WORK/login-response.json")"
    if [[ "$role" = organizer ]]; then
        ORGANIZER_JWT="$jwt"
        printf 'Authorization: Bearer %s\n' "$jwt" > "$WORK/organizer.headers"
    else
        BUYER_JWT="$jwt"
        ATTENDEE_EMAIL="$email"
    fi
    printf 'PASS: registered, email-activated, and logged in %s\n' "$role"
}
create_account organizer
create_account buyer
unset PASSWORD

python3 - "$WORK" "$RUN_ID" "$PRICE_KZT" <<'PY'
from datetime import datetime, timedelta, timezone
from pathlib import Path
from decimal import Decimal
import json, sys
root=Path(sys.argv[1]); now=datetime.now(timezone.utc)
iso=lambda dt: dt.isoformat(timespec='seconds').replace('+00:00','Z')
start=now+timedelta(days=7); end=start+timedelta(hours=3)
event=dict(title='E2E purchase '+sys.argv[2][:12], description='Automated development smoke test',
    category='Technology',visibility='UNLISTED',startAt=iso(start),endAt=iso(end),
    registrationWindow=dict(mode='ALWAYS_OPEN'))
venue=dict(name='E2E venue',address='Development fixture, Astana',capacity=10,seatingMode='GENERAL_ADMISSION')
ticket=dict(name='E2E admission',description='Smoke-test ticket',price=str(Decimal(sys.argv[3])),
    capacity=10,maxPerOrder=2,salesStartAt=iso(now-timedelta(days=1)),salesEndAt=iso(start-timedelta(hours=1)))
# BigDecimal accepts a JSON string, but emit the numeric amount explicitly.
ticket['price']=float(Decimal(sys.argv[3]))
for name,value in [('event',event),('venue',venue),('ticket',ticket)]:
    (root/(name+'.json')).write_text(json.dumps(value))
PY
request organizer POST /api/events "$WORK/event-response.json" 201 "$WORK/event.json"
EVENT_ID="$(jq -er '.id' "$WORK/event-response.json")"
request organizer POST "/api/events/$EVENT_ID/venue" "$WORK/venue-response.json" 204 "$WORK/venue.json"
request organizer POST "/api/events/$EVENT_ID/ticket-types/general-admission" "$WORK/ticket-response.json" 201 "$WORK/ticket.json"
TICKET_TYPE_ID="$(jq -er '.id' "$WORK/ticket-response.json")"
request organizer POST "/api/events/$EVENT_ID/publish" "$WORK/publish-response.json" 204
printf 'PASS: published event %s with ticket type %s (%s KZT)\n' "$EVENT_ID" "$TICKET_TYPE_ID" "$PRICE_KZT"

ACTIVATE="$(python3 - "$PRICE_KZT" <<'PY'
from decimal import Decimal
import sys
print(0 if Decimal(sys.argv[1]) == 0 else 1)
PY
)"
OUTPUT_DIR="${OUTPUT_DIR:-./purchase-e2e-$RUN_ID}"
export BASE_URL ORGANIZER_JWT BUYER_JWT EVENT_ID TICKET_TYPE_ID ATTENDEE_EMAIL ACTIVATE OUTPUT_DIR REFUND_ORDER
printf '\nStarting purchase checks. Paid mode needs two Halyk sandbox payments: activation fee, then ticket.\n'
bash "$E2E_SCRIPT"
printf '\nFixture event: %s\nBuyer email: %s\n' "$EVENT_ID" "$ATTENDEE_EMAIL"

