#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd -- "$SCRIPT_DIR/../../.." && pwd)
COMPOSE_FILE="$ROOT_DIR/scripts/docker-compose.yml"
DATABASE=course_concurrency
MYSQL_PASSWORD=${CON04_MYSQL_ROOT_PASSWORD:-root123}
REDIS_PASSWORD=${CON04_REDIS_PASSWORD:-redis123}

usage() {
  cat <<'EOF'
Usage: ./experiments/concurrency/data/con-04-redis.sh <action> [argument]

Actions:
  reset-single [index]    Delete cache/lock/status keys for con04_user_NNN (default: 1).
  reset-all               Delete cache/lock/status keys for every CON-04 user.
  expire-all <seconds>    Give every existing regular cache key the same short TTL.
  status-single [index]   Show EXISTS/TTL for one user's regular cache and build lock.
EOF
}

mysql_user_ids() {
  docker compose -f "$COMPOSE_FILE" exec -T \
    -e "MYSQL_PWD=$MYSQL_PASSWORD" mysql mysql -N -B -uroot "$DATABASE" \
    -e "SELECT id FROM user WHERE username REGEXP '^con04_user_[0-9]{3}$' ORDER BY username;"
}

mysql_user_id_by_index() {
  local index=$1
  local username
  username=$(printf 'con04_user_%03d' "$index")
  docker compose -f "$COMPOSE_FILE" exec -T \
    -e "MYSQL_PWD=$MYSQL_PASSWORD" mysql mysql -N -B -uroot "$DATABASE" \
    -e "SELECT id FROM user WHERE username = '$username' LIMIT 1;"
}

append_user_keys() {
  local user_id=$1
  REDIS_KEYS+=(
    # 前四项仅用于清理重构前的本地实验残留；应用运行时只使用 recommend:v2:*。
    "recommend:user:$user_id"
    "recommend:lock:user:$user_id"
    "recommend:cold:user:$user_id"
    "recommend:cold:lock:user:$user_id"
    "recommend:v2:user:$user_id"
    "recommend:v2:lock:user:$user_id"
    "recommend:v2:cold:user:$user_id"
    "recommend:v2:cold:lock:user:$user_id"
    "recommend:v2:version:user:$user_id"
    "recommend:cold:status:user:$user_id"
  )
}

delete_keys() {
  if (( ${#REDIS_KEYS[@]} == 0 )); then
    printf 'No CON-04 users found. Run con-04-prepare.sql first.\n' >&2
    exit 1
  fi
  docker compose -f "$COMPOSE_FILE" exec -T redis \
    redis-cli -a "$REDIS_PASSWORD" --no-auth-warning DEL "${REDIS_KEYS[@]}"
}

read_index() {
  local value=${1:-1}
  if [[ ! "$value" =~ ^[0-9]+$ ]] || (( value < 1 || value > 100 )); then
    printf 'User index must be between 1 and 100: %s\n' "$value" >&2
    exit 2
  fi
  printf '%s' "$value"
}

ACTION=${1:-}
if [[ -z "$ACTION" || "$ACTION" == "-h" || "$ACTION" == "--help" ]]; then
  usage
  exit 0
fi
shift

case "$ACTION" in
  reset-single)
    USER_INDEX=$(read_index "${1:-1}")
    USER_ID=$(mysql_user_id_by_index "$USER_INDEX")
    if [[ -z "$USER_ID" ]]; then
      printf 'CON-04 user %03d not found. Run con-04-prepare.sql first.\n' "$USER_INDEX" >&2
      exit 1
    fi
    REDIS_KEYS=()
    append_user_keys "$USER_ID"
    delete_keys
    printf 'Reset Redis keys for con04_user_%03d (userId=%s).\n' "$USER_INDEX" "$USER_ID"
    ;;
  reset-all)
    REDIS_KEYS=()
    while IFS= read -r user_id; do
      [[ -n "$user_id" ]] && append_user_keys "$user_id"
    done < <(mysql_user_ids)
    delete_keys
    printf 'Reset Redis keys for all CON-04 users.\n'
    ;;
  expire-all)
    SECONDS_VALUE=${1:-}
    if [[ ! "$SECONDS_VALUE" =~ ^[0-9]+$ ]] || (( SECONDS_VALUE < 1 )); then
      printf 'expire-all requires a positive number of seconds.\n' >&2
      exit 2
    fi
    CACHE_KEYS=()
    while IFS= read -r user_id; do
      [[ -n "$user_id" ]] && CACHE_KEYS+=("recommend:v2:user:$user_id")
    done < <(mysql_user_ids)
    if (( ${#CACHE_KEYS[@]} == 0 )); then
      printf 'No CON-04 users found. Run con-04-prepare.sql first.\n' >&2
      exit 1
    fi
    for cache_key in "${CACHE_KEYS[@]}"; do
      docker compose -f "$COMPOSE_FILE" exec -T redis \
        redis-cli -a "$REDIS_PASSWORD" --no-auth-warning EXPIRE "$cache_key" "$SECONDS_VALUE" >/dev/null
    done
    printf 'Applied TTL=%ss to existing CON-04 regular cache keys.\n' "$SECONDS_VALUE"
    ;;
  status-single)
    USER_INDEX=$(read_index "${1:-1}")
    USER_ID=$(mysql_user_id_by_index "$USER_INDEX")
    if [[ -z "$USER_ID" ]]; then
      printf 'CON-04 user %03d not found.\n' "$USER_INDEX" >&2
      exit 1
    fi
    for key in "recommend:v2:user:$USER_ID" "recommend:v2:lock:user:$USER_ID" \
      "recommend:v2:version:user:$USER_ID"; do
      EXISTS=$(docker compose -f "$COMPOSE_FILE" exec -T redis \
        redis-cli -a "$REDIS_PASSWORD" --no-auth-warning EXISTS "$key")
      TTL=$(docker compose -f "$COMPOSE_FILE" exec -T redis \
        redis-cli -a "$REDIS_PASSWORD" --no-auth-warning TTL "$key")
      printf '%s exists=%s ttl=%s\n' "$key" "$EXISTS" "$TTL"
    done
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac
