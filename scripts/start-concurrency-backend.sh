#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd -- "$SCRIPT_DIR/.." && pwd)
ENV_FILE="$ROOT_DIR/.env.local"
JAR_FILE="$ROOT_DIR/backend/target/course-system-0.0.1-SNAPSHOT.jar"
EXPERIMENT_MAIN_CLASS=com.sy.course_system.experiment.ConcurrencyExperimentApplication

usage() {
  cat <<'EOF'
Usage: ./scripts/start-concurrency-backend.sh [port]

Start one backend instance for concurrency experiments.

Arguments:
  port    HTTP port for this instance; defaults to 8080.

Examples:
  ./scripts/start-concurrency-backend.sh
  ./scripts/start-concurrency-backend.sh 8081

One-time CON-03 rollback fault injection (test classpath only):
  CON03_FAIL_AFTER_STUDY_INSERT_ENABLED=true \
  CON03_FAIL_AFTER_STUDY_INSERT_EVENT_ID=con03-rollback-event \
  ./scripts/start-concurrency-backend.sh 8080
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if (( $# > 1 )); then
  usage >&2
  exit 2
fi

SERVER_PORT_VALUE=${1:-8080}
if [[ ! "$SERVER_PORT_VALUE" =~ ^[0-9]+$ ]]; then
  printf 'Invalid port: %s\n' "$SERVER_PORT_VALUE" >&2
  exit 2
fi
SERVER_PORT_NUMBER=$((10#$SERVER_PORT_VALUE))
if (( SERVER_PORT_NUMBER < 1 || SERVER_PORT_NUMBER > 65535 )); then
  printf 'Invalid port: %s\n' "$SERVER_PORT_VALUE" >&2
  exit 2
fi

if [[ ! -f "$ENV_FILE" ]]; then
  printf 'Missing environment file: %s\n' "$ENV_FILE" >&2
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  printf 'java is not available on PATH.\n' >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

export SERVER_PORT="$SERVER_PORT_NUMBER"
export DB_NAME=course_concurrency
export RECOMMEND_SCORE_SNAPSHOT_REBUILD_ON_STARTUP=false
export RECOMMEND_HOT_SYNC_ENABLED=false

if [[ "${CON03_FAIL_AFTER_STUDY_INSERT_ENABLED:-false}" == "true" ]]; then
  FAULT_EVENT_ID=${CON03_FAIL_AFTER_STUDY_INSERT_EVENT_ID:-}
  if [[ ! "$FAULT_EVENT_ID" =~ ^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$ ]]; then
    printf 'A valid CON03_FAIL_AFTER_STUDY_INSERT_EVENT_ID is required when fault injection is enabled.\n' >&2
    exit 2
  fi
elif [[ ! -f "$JAR_FILE" ]]; then
  printf 'Missing backend JAR: %s\n' "$JAR_FILE" >&2
  printf 'Build it first with: cd backend && ./mvnw -q -DskipTests package\n' >&2
  exit 1
fi

printf '[concurrency-backend] port: %s\n' "$SERVER_PORT"
printf '[concurrency-backend] database: %s\n' "$DB_NAME"
if [[ "${CON03_FAIL_AFTER_STUDY_INSERT_ENABLED:-false}" == "true" ]]; then
  printf '[concurrency-backend] launcher: test classpath (%s)\n' "$EXPERIMENT_MAIN_CLASS"
  printf '[concurrency-backend] one-time STUDY insert fault event: %s\n' "$FAULT_EVENT_ID"
  cd "$ROOT_DIR/backend"
  exec ./mvnw -q -DskipTests test-compile spring-boot:test-run \
    "-Dspring-boot.run.main-class=$EXPERIMENT_MAIN_CLASS"
fi

printf '[concurrency-backend] jar: %s\n' "$JAR_FILE"
exec java -jar "$JAR_FILE"
