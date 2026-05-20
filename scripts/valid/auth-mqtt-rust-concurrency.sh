#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BROKER_PORT="${BROKER_PORT:-1884}"
HTTP_PORT="${HTTP_PORT:-18098}"
DB_PATH="${DB_PATH:-$ROOT_DIR/data/auth-service-concurrency.db}"
TMP_CFG="$(mktemp)"
CONCURRENCY="${CONCURRENCY:-10}"
CURL_MAX_TIME="${CURL_MAX_TIME:-10}"

cleanup() {
  if [[ -n "${JAVA_PID:-}" ]]; then kill "$JAVA_PID" >/dev/null 2>&1 || true; fi
  if [[ -n "${WORKER_PID:-}" ]]; then kill "$WORKER_PID" >/dev/null 2>&1 || true; fi
  if [[ -n "${BROKER_PID:-}" ]]; then kill "$BROKER_PID" >/dev/null 2>&1 || true; fi
  rm -f "$TMP_CFG"
}
trap cleanup EXIT

command -v mosquitto >/dev/null 2>&1 || { echo "mosquitto missing"; exit 1; }
command -v mosquitto_pub >/dev/null 2>&1 || { echo "mosquitto_pub missing"; exit 1; }
command -v mosquitto_sub >/dev/null 2>&1 || { echo "mosquitto_sub missing"; exit 1; }

mkdir -p "$ROOT_DIR/data"
mosquitto -p "$BROKER_PORT" >/tmp/mosquitto-concurrency.log 2>&1 &
BROKER_PID=$!
sleep 1

(
  cd "$ROOT_DIR/backend/rust/database-conector-sqlite"
  MQTT_HOST=127.0.0.1 MQTT_PORT="$BROKER_PORT" SQLITE_DB_PATH="$DB_PATH" DB_USER_REQUEST_TOPIC="portal/db/user/request" \
    cargo run --quiet --bin db-mqtt-worker >/tmp/db-mqtt-worker-concurrency.log 2>&1 &
  echo $! > /tmp/db-mqtt-worker-concurrency.pid
)
WORKER_PID="$(cat /tmp/db-mqtt-worker-concurrency.pid)"
sleep 2

sed -e "s/http_port = 8080/http_port = ${HTTP_PORT}/" -e "s/port = 1883/port = ${BROKER_PORT}/" "$ROOT_DIR/config/portal-config.toml" > "$TMP_CFG"

(
  cd "$ROOT_DIR"
  java -cp "backend/java/portal/shared/domain/target/classes:backend/java/portal/services/auth-service/target/classes" \
    com.portal.auth.AuthApplication "$TMP_CFG" >/tmp/auth-service-concurrency.log 2>&1 &
  echo $! > /tmp/auth-service-concurrency.pid
)
JAVA_PID="$(cat /tmp/auth-service-concurrency.pid)"

for _ in $(seq 1 40); do
  if ! kill -0 "$JAVA_PID" >/dev/null 2>&1; then
    echo "auth-service cayó durante arranque. Log:"
    cat /tmp/auth-service-concurrency.log
    exit 1
  fi
  if curl -sS "http://127.0.0.1:${HTTP_PORT}/health" >/dev/null 2>&1; then
    break
  fi
  sleep 0.5
done

for i in $(seq 1 "$CONCURRENCY"); do
  (
    EMAIL="cc-${i}-$(date +%s)-$RANDOM@example.com"
    curl --max-time "$CURL_MAX_TIME" --connect-timeout 2 -sS -X POST "http://127.0.0.1:${HTTP_PORT}/auth/register" -H 'Content-Type: application/json' \
      -d "{\"template\":\"casa\",\"firstName\":\"C${i}\",\"lastName\":\"T\",\"email\":\"${EMAIL}\",\"mobile\":\"+52557000${i}\",\"password\":\"abc123\"}" >/tmp/cc-${i}.json
  ) &
done
wait

HEALTH="$(curl --max-time "$CURL_MAX_TIME" --connect-timeout 2 -sS "http://127.0.0.1:${HTTP_PORT}/health/db-mqtt")"
METRICS="$(curl --max-time "$CURL_MAX_TIME" --connect-timeout 2 -sS "http://127.0.0.1:${HTTP_PORT}/metrics/db-mqtt")"
echo "$HEALTH"
echo "$METRICS"
[[ "$HEALTH" == *'"healthy":true'* ]]
[[ "$METRICS" == *'"totalCalls":'* ]]

echo "auth-mqtt-rust-concurrency: OK"
