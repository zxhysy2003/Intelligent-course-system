#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd -- "$SCRIPT_DIR/.." && pwd)
ENV_FILE="$ROOT_DIR/.env.local"
JAR_FILE="$ROOT_DIR/backend/target/course-system-0.0.1-SNAPSHOT.jar"

usage() {
  cat <<'EOF'
Usage: ./scripts/start-con04-backend.sh [port] [stub-url]

Defaults:
  port      8080
  stub-url  http://127.0.0.1:18000

The script always uses course_concurrency and disables unrelated background work.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi
if (( $# > 2 )); then
  usage >&2
  exit 2
fi

SERVER_PORT_VALUE=${1:-8080}
STUB_URL_VALUE=${2:-http://127.0.0.1:18000}
if [[ ! "$SERVER_PORT_VALUE" =~ ^[0-9]+$ ]] || (( SERVER_PORT_VALUE < 1 || SERVER_PORT_VALUE > 65535 )); then
  printf 'Invalid port: %s\n' "$SERVER_PORT_VALUE" >&2
  exit 2
fi
if [[ ! "$STUB_URL_VALUE" =~ ^https?:// ]]; then
  printf 'Invalid stub URL: %s\n' "$STUB_URL_VALUE" >&2
  exit 2
fi
if [[ ! -f "$ENV_FILE" ]]; then
  printf 'Missing environment file: %s\n' "$ENV_FILE" >&2
  exit 1
fi
if [[ ! -f "$JAR_FILE" ]]; then
  printf 'Missing backend JAR: %s\n' "$JAR_FILE" >&2
  printf 'Build it first with: cd backend && ./mvnw -q -DskipTests package\n' >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

export SERVER_PORT="$SERVER_PORT_VALUE"
export DB_NAME=course_concurrency
export RECOMMEND_SERVICE_URL="${STUB_URL_VALUE%/}"
export RECOMMEND_SCORE_SNAPSHOT_REBUILD_ON_STARTUP=false
export RECOMMEND_HOT_SYNC_ENABLED=false
export RECOMMEND_NEW_COURSE_ENABLED=false
export RECOMMEND_ASYNC_ENABLED=false
export RECOMMEND_CACHE_WAIT_RETRY_TIMES=${CON04_WAIT_RETRY_TIMES:-3}
export RECOMMEND_CACHE_WAIT_MILLIS=${CON04_WAIT_MILLIS:-80}
export RECOMMEND_CACHE_INITIAL_BUILD_WAIT_MILLIS=${CON04_INITIAL_BUILD_WAIT_MILLIS:-2500}
export RECOMMEND_CACHE_BUILD_LOCK_TTL_SECONDS=${CON04_BUILD_LOCK_TTL_SECONDS:-20}
export RECOMMEND_CACHE_TTL_MINUTES=${CON04_REGULAR_TTL_MINUTES:-30}
export RECOMMEND_CACHE_TTL_JITTER_MINUTES=${CON04_REGULAR_TTL_JITTER_MINUTES:-10}
export RECOMMEND_CACHE_STALE_RETENTION_MINUTES=${CON04_STALE_RETENTION_MINUTES:-60}
export RECOMMEND_CACHE_BUILD_CORE_SIZE=${CON04_BUILD_CORE_SIZE:-2}
export RECOMMEND_CACHE_BUILD_MAX_SIZE=${CON04_BUILD_MAX_SIZE:-4}
export RECOMMEND_CACHE_BUILD_QUEUE_CAPACITY=${CON04_BUILD_QUEUE_CAPACITY:-16}

printf '[con04-backend] port: %s\n' "$SERVER_PORT"
printf '[con04-backend] database: %s\n' "$DB_NAME"
printf '[con04-backend] recommend stub: %s\n' "$RECOMMEND_SERVICE_URL"
printf '[con04-backend] wait budget: %s x %sms\n' \
  "$RECOMMEND_CACHE_WAIT_RETRY_TIMES" "$RECOMMEND_CACHE_WAIT_MILLIS"
printf '[con04-backend] initial build wait: %sms\n' "$RECOMMEND_CACHE_INITIAL_BUILD_WAIT_MILLIS"
printf '[con04-backend] lock TTL: %ss, logical TTL: %smin + 0..%smin jitter\n' \
  "$RECOMMEND_CACHE_BUILD_LOCK_TTL_SECONDS" "$RECOMMEND_CACHE_TTL_MINUTES" \
  "$RECOMMEND_CACHE_TTL_JITTER_MINUTES"
printf '[con04-backend] build pool: core=%s max=%s queue=%s\n' \
  "$RECOMMEND_CACHE_BUILD_CORE_SIZE" "$RECOMMEND_CACHE_BUILD_MAX_SIZE" \
  "$RECOMMEND_CACHE_BUILD_QUEUE_CAPACITY"

exec java -jar "$JAR_FILE"
