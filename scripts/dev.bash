#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
ROOT_PARENT="$(cd -- "$ROOT_DIR/.." && pwd -P)"

export FRONTEND_HOST="${FRONTEND_HOST:-127.0.0.1}"
export FRONTEND_PORT="${FRONTEND_PORT:-5173}"
export BACKEND_HOST="${BACKEND_HOST:-127.0.0.1}"
export BACKEND_PORT="${BACKEND_PORT:-8080}"
export RECOMMEND_HOST="${RECOMMEND_HOST:-127.0.0.1}"
export RECOMMEND_PORT="${RECOMMEND_PORT:-8000}"
export RECOMMEND_CONDA_ENV="${RECOMMEND_CONDA_ENV:-lab_autumn}"

export DB_HOST="${DB_HOST:-127.0.0.1}"
export DB_PORT="${DB_PORT:-3306}"
export DB_NAME="${DB_NAME:-course_db}"
export DB_USERNAME="${DB_USERNAME:-dev}"
export DB_PASSWORD="${DB_PASSWORD:-dev123}"
export REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
export REDIS_PORT="${REDIS_PORT:-6379}"
export REDIS_PASSWORD="${REDIS_PASSWORD:-redis123}"
export NEO4J_URI="${NEO4J_URI:-bolt://127.0.0.1:7687}"
export NEO4J_USERNAME="${NEO4J_USERNAME:-neo4j}"
export NEO4J_PASSWORD="${NEO4J_PASSWORD:-neo4j123}"
export VIDEO_DIR="${VIDEO_DIR:-${ROOT_PARENT}/course_videos}"
export VIDEO_BASE_URL="${VIDEO_BASE_URL:-http://${BACKEND_HOST}:${BACKEND_PORT}}"
export RECOMMEND_SERVICE_URL="${RECOMMEND_SERVICE_URL:-http://${RECOMMEND_HOST}:${RECOMMEND_PORT}}"
export VITE_BACKEND_TARGET="${VITE_BACKEND_TARGET:-http://${BACKEND_HOST}:${BACKEND_PORT}}"
export CORS_ALLOWED_ORIGIN_PATTERNS="${CORS_ALLOWED_ORIGIN_PATTERNS:-http://localhost:${FRONTEND_PORT},http://127.0.0.1:${FRONTEND_PORT},http://192.168.*:${FRONTEND_PORT}}"
export SERVER_PORT="${SERVER_PORT:-${BACKEND_PORT}}"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"

pids=()
STATE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/course-system-dev.XXXXXX")"

log() {
  printf '[dev] %s\n' "$*"
}

cleanup() {
  local status="${1:-0}"
  trap - EXIT INT TERM

  if ((${#pids[@]} > 0)); then
    log "stopping services..."
    for pid in "${pids[@]}"; do
      kill "$pid" >/dev/null 2>&1 || true
    done
    wait >/dev/null 2>&1 || true
  fi

  rm -rf "$STATE_DIR"

  exit "$status"
}

start_service() {
  local name="$1"
  local dir="$2"
  shift 2

  log "starting ${name}"
  (
    set +e
    cd "$dir" || exit 1
    printf '[%s] %s\n' "$name" "$*"
    "$@" &
    child_pid=$!
    trap 'kill "$child_pid" >/dev/null 2>&1; wait "$child_pid" >/dev/null 2>&1; exit 143' INT TERM
    wait "$child_pid"
    child_status=$?
    printf '%s\n' "$child_status" > "$STATE_DIR/${name}.exit"
    exit "$child_status"
  ) &
  pids+=("$!")
}

trap 'cleanup 130' INT TERM
trap 'cleanup $?' EXIT

if [[ ! -d "$ROOT_DIR/frontend/node_modules" ]]; then
  log "frontend dependencies are missing; run 'cd frontend && npm install' once if this service fails to start."
fi

recommend_cmd=()
if command -v conda >/dev/null 2>&1; then
  recommend_cmd=(conda run --no-capture-output -n "$RECOMMEND_CONDA_ENV" uvicorn main:app --reload --host "$RECOMMEND_HOST" --port "$RECOMMEND_PORT")
else
  recommend_cmd=(python3 -m uvicorn main:app --reload --host "$RECOMMEND_HOST" --port "$RECOMMEND_PORT")
fi

log "frontend:          http://${FRONTEND_HOST}:${FRONTEND_PORT}"
log "backend:           http://${BACKEND_HOST}:${BACKEND_PORT}"
log "recommend-service: http://${RECOMMEND_HOST}:${RECOMMEND_PORT}"
log "spring profile:    ${SPRING_PROFILES_ACTIVE}"

start_service "recommend-service" "$ROOT_DIR/recommend-service" "${recommend_cmd[@]}"
start_service "backend" "$ROOT_DIR/backend" ./mvnw spring-boot:run
start_service "frontend" "$ROOT_DIR/frontend" npm run dev -- --host "$FRONTEND_HOST" --port "$FRONTEND_PORT"

while true; do
  for exit_file in "$STATE_DIR"/*.exit; do
    [[ -e "$exit_file" ]] || continue
    service_name="$(basename "$exit_file" .exit)"
    status="$(<"$exit_file")"
    log "${service_name} exited with status ${status}; shutting down the rest"
    cleanup "$status"
  done
  sleep 1
done
