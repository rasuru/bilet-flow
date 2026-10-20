#!/usr/bin/env bash
# Local development entry point. Invoke with bash from any shell.
set -euo pipefail
umask 077
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

fail() { printf 'Error: %s\n' "$*" >&2; exit 1; }
init_env() {
    if [[ -f .env.local ]]; then
        printf '.env.local already exists; settings and encryption key retained.\n'
        return
    fi
    command -v openssl >/dev/null || fail 'Install openssl to generate the payment encryption key.'
    [[ -f .env.example ]] || fail 'Missing .env.example.'
    local key
    key="$(openssl rand -base64 32)"
    # noclobber prevents a concurrent initialization from replacing local secrets.
    (
        set -o noclobber
        {
            cat .env.example
            printf '\n# Generated once by dev.sh init; do not regenerate on each startup.\n'
            printf "BILETFLOW_PAYMENTS_SESSION_ENCRYPTION_KEY='%s'\n" "$key"
        } > .env.local
    )
    chmod 600 .env.local
    printf 'Created .env.local with a stable encryption key. Add Halyk sandbox credentials for paid checkout.\n'
}
load_env() {
    [[ -f .env.local ]] || init_env
    set -a
    # shellcheck disable=SC1091
    source ./.env.local
    set +a
    [[ -n "${BILETFLOW_PAYMENTS_SESSION_ENCRYPTION_KEY:-}" ]] || fail 'Set BILETFLOW_PAYMENTS_SESSION_ENCRYPTION_KEY in .env.local (base64, 32 bytes).'
    if [[ -z "${BILETFLOW_PAYMENTS_HALYK_CLIENT_SECRET:-}" ]]; then
        printf 'Halyk sandbox secret is unset; paid payment sessions will be unavailable.\n'
    fi
}
compose() {
    command -v docker >/dev/null || fail 'Install Docker with Docker Compose.'
    docker compose -f src/main/docker/services.yml "$@"
}

command="${1:-start}"
if [[ "$#" -gt 0 ]]; then shift; fi
case "$command" in
    init)
        [[ "$#" -eq 0 ]] || fail 'Usage: bash dev.sh init'
        init_env
        ;;
    start)
        load_env
        compose up --wait
        printf '\nBackend: http://localhost:8080\nMailpit: http://localhost:8025\nCtrl+C stops the backend; Docker services remain running.\n\n'
        # Gradle's dev configuration selects the Spring dev profile.
        # Plain console avoids the persistent progress display during bootRun.
        exec bash ./gradlew --console=plain bootRun "$@"
        ;;
    services)
        [[ "$#" -eq 0 ]] || fail 'Usage: bash dev.sh services'
        compose up --wait
        ;;
    stop)
        [[ "$#" -eq 0 ]] || fail 'Usage: bash dev.sh stop'
        compose stop
        ;;
    help|--help|-h)
        printf '%s\n' \
            'Usage: bash dev.sh [start|init|services|stop]' \
            '  start     Load .env.local, start dependencies, run the backend (default).' \
            '  init      Create .env.local and a stable payment encryption key once.' \
            '  services  Start PostgreSQL, Redis, Elasticsearch, and Mailpit.' \
            '  stop      Stop dependency containers, retaining their data.' \
            '  start accepts additional Gradle arguments after the command.'
        ;;
    *) fail "Unknown command: $command. Run: bash dev.sh help" ;;
esac

