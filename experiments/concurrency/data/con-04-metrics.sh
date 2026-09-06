#!/usr/bin/env bash
set -euo pipefail

BASE_URL=${CON04_METRICS_BASE_URL:-http://127.0.0.1:8080}
ADMIN_TOKEN=${CON04_ADMIN_TOKEN:-}
ADMIN_USERNAME=${CON04_ADMIN_USERNAME:-con04_admin}
ADMIN_PASSWORD=${CON04_ADMIN_PASSWORD:-123456}
DEFAULT_BEFORE_FILE=${CON04_METRICS_BEFORE_FILE:-/tmp/con04-e-metrics-before.tsv}
DEFAULT_AFTER_FILE=${CON04_METRICS_AFTER_FILE:-/tmp/con04-e-metrics-after.tsv}
EVENTS=(refresh_rejected degraded wait_timeout)

usage() {
  cat <<'EOF'
Usage: ./experiments/concurrency/data/con-04-metrics.sh <action> [arguments]

Actions:
  before [file]             Capture the pre-run counters.
  after [before] [after]    Capture the post-run counters and print their deltas.
  snapshot <file>           Capture counters to a specified file.
  diff <before> <after>     Print deltas from two existing snapshots.

Environment:
  CON04_METRICS_BASE_URL    Backend to query (default: http://127.0.0.1:8080).
  CON04_ADMIN_TOKEN         Existing administrator token; skips login when set.
  CON04_ADMIN_USERNAME      Login username (default: con04_admin).
  CON04_ADMIN_PASSWORD      Login password (default: 123456).
  CON04_METRICS_BEFORE_FILE Default file used by before/after.
  CON04_METRICS_AFTER_FILE  Default file used by after.

Examples:
  ./experiments/concurrency/data/con-04-metrics.sh before
  # Run the CON-04 E k6 command without restarting the backend.
  ./experiments/concurrency/data/con-04-metrics.sh after
EOF
}

require_command() {
  local command_name=$1
  if ! command -v "$command_name" >/dev/null 2>&1; then
    printf 'Required command not found: %s\n' "$command_name" >&2
    exit 1
  fi
}

split_http_response() {
  local response=$1
  HTTP_STATUS=${response##*$'\n'}
  HTTP_BODY=${response%$'\n'*}
}

load_admin_token() {
  if [[ -n "$ADMIN_TOKEN" ]]; then
    return
  fi

  local payload
  local response
  local token
  payload=$(jq -nc \
    --arg username "$ADMIN_USERNAME" \
    --arg password "$ADMIN_PASSWORD" \
    '{username: $username, password: $password}')

  if ! response=$(curl -sS -X POST \
    -H 'Content-Type: application/json' \
    -H 'Accept: application/json' \
    --data-binary "$payload" \
    -w $'\n%{http_code}' \
    "${BASE_URL%/}/api/v1/auth/login"); then
    printf 'Failed to connect to the backend while logging in: %s\n' "$BASE_URL" >&2
    return 1
  fi

  split_http_response "$response"
  if [[ "$HTTP_STATUS" != "200" ]]; then
    printf 'Administrator login failed: HTTP %s\n' "$HTTP_STATUS" >&2
    printf '%s\n' "$HTTP_BODY" >&2
    return 1
  fi

  if ! token=$(jq -er '
    if .code == 200 and (.data | type) == "string" and (.data | length) > 0
    then .data
    else empty
    end
  ' <<< "$HTTP_BODY"); then
    printf 'Administrator login returned no usable token.\n' >&2
    return 1
  fi
  ADMIN_TOKEN=$token
}

metric_count() {
  local event=$1
  local response
  local count

  if ! response=$(curl -sS \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H 'Accept: application/json' \
    -w $'\n%{http_code}' \
    "${BASE_URL%/}/actuator/metrics/recommend.cache.events?tag=event:$event"); then
    printf 'Failed to query metric event=%s from %s\n' "$event" "$BASE_URL" >&2
    return 1
  fi

  split_http_response "$response"
  case "$HTTP_STATUS" in
    200)
      if ! count=$(jq -er '
        [.measurements[] | select(.statistic == "COUNT") | .value]
        | if length == 1 and (.[0] | type) == "number" and .[0] >= 0
          then .[0]
          else empty
          end
      ' <<< "$HTTP_BODY"); then
        printf 'Metric event=%s returned an invalid COUNT measurement.\n' "$event" >&2
        return 1
      fi
      printf '%s\n' "$count"
      ;;
    404)
      # Micrometer registers event counters lazily. A never-seen event is zero.
      printf '0\n'
      ;;
    *)
      printf 'Metric query failed for event=%s: HTTP %s\n' "$event" "$HTTP_STATUS" >&2
      printf '%s\n' "$HTTP_BODY" >&2
      return 1
      ;;
  esac
}

capture_snapshot() {
  local output_file=$1
  local output_dir
  local temp_file
  local event
  local count

  output_dir=$(dirname -- "$output_file")
  if [[ ! -d "$output_dir" ]]; then
    printf 'Snapshot directory does not exist: %s\n' "$output_dir" >&2
    return 1
  fi

  temp_file=$(mktemp "${output_file}.tmp.XXXXXX")
  printf '# sampled_at=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" > "$temp_file"
  printf '# base_url=%s\n' "$BASE_URL" >> "$temp_file"

  for event in "${EVENTS[@]}"; do
    if ! count=$(metric_count "$event"); then
      rm -f -- "$temp_file"
      return 1
    fi
    printf '%s\t%s\n' "$event" "$count" >> "$temp_file"
  done

  mv -- "$temp_file" "$output_file"
  printf 'Saved metric snapshot: %s\n' "$output_file"
  awk -F '\t' '!/^#/ && NF == 2 { printf "  %-20s %s\n", $1, $2 }' "$output_file"
}

validate_snapshot() {
  local snapshot_file=$1
  if [[ ! -f "$snapshot_file" ]]; then
    printf 'Snapshot file does not exist: %s\n' "$snapshot_file" >&2
    return 1
  fi

  if ! awk -F '\t' '
    BEGIN {
      required["refresh_rejected"] = 1
      required["degraded"] = 1
      required["wait_timeout"] = 1
    }
    /^#/ || NF == 0 { next }
    !($1 in required) { invalid = 1; next }
    $2 !~ /^[0-9]+([.][0-9]+)?([eE][+-]?[0-9]+)?$/ { invalid = 1; next }
    { seen[$1]++ }
    END {
      for (event in required) {
        if (seen[event] != 1) invalid = 1
      }
      exit invalid
    }
  ' "$snapshot_file"; then
    printf 'Invalid metric snapshot: %s\n' "$snapshot_file" >&2
    return 1
  fi
}

print_diff() {
  local before_file=$1
  local after_file=$2
  validate_snapshot "$before_file"
  validate_snapshot "$after_file"

  awk -F '\t' '
    BEGIN {
      printf "%-20s %12s %12s %12s\n", "event", "before", "after", "delta"
    }
    /^#/ || NF == 0 { next }
    NR == FNR { before[$1] = $2; next }
    {
      printf "%-20s %12g %12g %12g\n", $1, before[$1], $2, $2 - before[$1]
      if ($2 < before[$1]) restarted = 1
    }
    END {
      if (restarted) {
        print "Counter decreased: the backend may have restarted between snapshots." > "/dev/stderr"
        exit 1
      }
    }
  ' "$before_file" "$after_file"
}

if (( $# == 0 )); then
  usage
  exit 0
fi

ACTION=$1
shift

case "$ACTION" in
  -h|--help|help)
    usage
    ;;
  before)
    if (( $# > 1 )); then
      usage >&2
      exit 2
    fi
    require_command curl
    require_command jq
    load_admin_token
    capture_snapshot "${1:-$DEFAULT_BEFORE_FILE}"
    ;;
  after)
    if (( $# > 2 )); then
      usage >&2
      exit 2
    fi
    require_command curl
    require_command jq
    load_admin_token
    BEFORE_FILE=${1:-$DEFAULT_BEFORE_FILE}
    AFTER_FILE=${2:-$DEFAULT_AFTER_FILE}
    validate_snapshot "$BEFORE_FILE"
    capture_snapshot "$AFTER_FILE"
    print_diff "$BEFORE_FILE" "$AFTER_FILE"
    ;;
  snapshot)
    if (( $# != 1 )); then
      usage >&2
      exit 2
    fi
    require_command curl
    require_command jq
    load_admin_token
    capture_snapshot "$1"
    ;;
  diff)
    if (( $# != 2 )); then
      usage >&2
      exit 2
    fi
    print_diff "$1" "$2"
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac
