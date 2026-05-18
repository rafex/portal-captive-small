#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BROKER_PORT="${BROKER_PORT:-1883}"
DB_PATH="${DB_PATH:-$ROOT_DIR/data/auth-service.db}"

mkdir -p "$ROOT_DIR/data"

if ! command -v mosquitto >/dev/null 2>&1; then
  echo "mosquitto not installed"
  exit 1
fi

if ! command -v mosquitto_pub >/dev/null 2>&1 || ! command -v mosquitto_sub >/dev/null 2>&1; then
  echo "mosquitto clients not installed"
  exit 1
fi

cleanup() {
  if [[ -n "${WORKER_PID:-}" ]]; then
    kill "$WORKER_PID" >/dev/null 2>&1 || true
  fi
  if [[ -n "${BROKER_PID:-}" ]]; then
    kill "$BROKER_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

mosquitto -p "$BROKER_PORT" >/tmp/mosquitto-local.log 2>&1 &
BROKER_PID=$!
sleep 1

(
  cd "$ROOT_DIR/backend/rust/database-conector-sqlite"
  MQTT_HOST=127.0.0.1 MQTT_PORT="$BROKER_PORT" SQLITE_DB_PATH="$DB_PATH" DB_USER_REQUEST_TOPIC="portal/db/user/request" \
    cargo run --quiet --bin db-mqtt-worker >/tmp/db-mqtt-worker.log 2>&1 &
  echo $! > /tmp/db-mqtt-worker.pid
)
WORKER_PID="$(cat /tmp/db-mqtt-worker.pid)"
sleep 2

bash "$ROOT_DIR/scripts/valid/db-mqtt-e2e.sh"

echo "db-mqtt-local-stack: OK"
