#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BROKER_PORT="${BROKER_PORT:-1883}"
HTTP_PORT="${HTTP_PORT:-18096}"
DB_PATH="${DB_PATH:-$ROOT_DIR/data/auth-service-e2e.db}"
TMP_CFG="$(mktemp)"

cleanup() {
  if [[ -n "${JAVA_PID:-}" ]]; then kill "$JAVA_PID" >/dev/null 2>&1 || true; fi
  if [[ -n "${WORKER_PID:-}" ]]; then kill "$WORKER_PID" >/dev/null 2>&1 || true; fi
  if [[ -n "${BROKER_PID:-}" ]]; then kill "$BROKER_PID" >/dev/null 2>&1 || true; fi
  rm -f "$TMP_CFG"
}
trap cleanup EXIT

if ! command -v mosquitto >/dev/null 2>&1; then
  echo "mosquitto not installed"
  exit 1
fi
if ! command -v mosquitto_pub >/dev/null 2>&1 || ! command -v mosquitto_sub >/dev/null 2>&1; then
  echo "mosquitto clients not installed"
  exit 1
fi

mkdir -p "$ROOT_DIR/data"

mosquitto -p "$BROKER_PORT" >/tmp/mosquitto-auth-e2e.log 2>&1 &
BROKER_PID=$!
sleep 1

(
  cd "$ROOT_DIR/backend/rust/database-conector-sqlite"
  MQTT_HOST=127.0.0.1 MQTT_PORT="$BROKER_PORT" SQLITE_DB_PATH="$DB_PATH" DB_USER_REQUEST_TOPIC="portal/db/user/request" \
    cargo run --quiet --bin db-mqtt-worker >/tmp/db-mqtt-worker-auth-e2e.log 2>&1 &
  echo $! > /tmp/db-mqtt-worker-auth-e2e.pid
)
WORKER_PID="$(cat /tmp/db-mqtt-worker-auth-e2e.pid)"
sleep 2

sed \
  -e "s/http_port = 8080/http_port = ${HTTP_PORT}/" \
  -e 's/type = "mqtt_rust"/type = "mqtt_rust"/' \
  -e "s/port = 1883/port = ${BROKER_PORT}/" \
  "$ROOT_DIR/config/portal-config.toml" > "$TMP_CFG"

(
  cd "$ROOT_DIR"
  java -cp "backend/java/portal/shared/domain/target/classes:backend/java/portal/services/auth-service/target/classes" \
    com.portal.auth.AuthApplication "$TMP_CFG" >/tmp/auth-service-mqtt-rust-e2e.log 2>&1 &
  echo $! > /tmp/auth-service-mqtt-rust-e2e.pid
)
JAVA_PID="$(cat /tmp/auth-service-mqtt-rust-e2e.pid)"

for _ in $(seq 1 40); do
  if ! kill -0 "$JAVA_PID" >/dev/null 2>&1; then
    echo "auth-service cayó durante arranque. Log:"
    cat /tmp/auth-service-mqtt-rust-e2e.log
    exit 1
  fi
  if curl -sS "http://127.0.0.1:${HTTP_PORT}/health" >/dev/null 2>&1; then
    break
  fi
  sleep 0.5
done

REQ_SUFFIX="$(date +%s)"
REGISTER_PAYLOAD="{\"template\":\"casa\",\"firstName\":\"E2E\",\"lastName\":\"Rust\",\"email\":\"e2e-${REQ_SUFFIX}@example.com\",\"mobile\":\"+525566677788\",\"password\":\"abc123\"}"
LOGIN_PAYLOAD="{\"identifier\":\"e2e-${REQ_SUFFIX}@example.com\",\"password\":\"abc123\"}"

REG_BODY="$(curl -sS -X POST "http://127.0.0.1:${HTTP_PORT}/auth/register" -H 'Content-Type: application/json' -d "$REGISTER_PAYLOAD")"
[[ "$REG_BODY" == *"userId"* ]]

LOGIN_BODY="$(curl -sS -X POST "http://127.0.0.1:${HTTP_PORT}/auth/login" -H 'Content-Type: application/json' -d "$LOGIN_PAYLOAD")"
[[ "$LOGIN_BODY" == *'"authenticated":true'* ]]

DB_COUNT="$(sqlite3 "$DB_PATH" 'select count(*) from users;')"
[[ "$DB_COUNT" =~ ^[0-9]+$ ]]
[[ "$DB_COUNT" -ge 1 ]]

echo "auth-mqtt-rust-e2e: OK"
