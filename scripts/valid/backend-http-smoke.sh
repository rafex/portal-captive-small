#!/usr/bin/env bash
set -euo pipefail

PORT="${PORT:-18090}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP_CFG="$(mktemp)"

cleanup() {
  if [[ -n "${PID:-}" ]]; then
    kill "${PID}" >/dev/null 2>&1 || true
  fi
  rm -f "$TMP_CFG"
}
trap cleanup EXIT

# Force memory repository for local/CI smoke (no hard dependency on MQTT worker)
sed -e "s/http_port = 8080/http_port = ${PORT}/" -e 's/type = "mqtt_rust"/type = "memory"/' "$ROOT_DIR/config/portal-config.toml" > "$TMP_CFG"

(
  cd "$ROOT_DIR"
  java -cp "backend/java/portal/shared/domain/target/classes:backend/java/portal/services/auth-service/target/classes" \
    com.portal.auth.AuthApplication "$TMP_CFG" >/tmp/auth-service-smoke.log 2>&1 &
  echo $! > /tmp/auth-service-smoke.pid
)
PID="$(cat /tmp/auth-service-smoke.pid)"

# Wait until service is reachable
for _ in $(seq 1 40); do
  if ! kill -0 "$PID" >/dev/null 2>&1; then
    echo "auth-service terminó antes de estar listo. Log:"
    cat /tmp/auth-service-smoke.log
    exit 1
  fi
  if curl -sS "http://127.0.0.1:${PORT}/health" >/dev/null 2>&1; then
    break
  fi
  sleep 0.5
done

curl -sS "http://127.0.0.1:${PORT}/health" >/dev/null

REQ_SUFFIX="$(date +%s)"
REGISTER_PAYLOAD="{\"template\":\"casa\",\"firstName\":\"Smoke\",\"lastName\":\"Test\",\"email\":\"smoke-${REQ_SUFFIX}@example.com\",\"mobile\":\"+525500001111\",\"password\":\"abc123\"}"
LOGIN_PAYLOAD="{\"identifier\":\"smoke-${REQ_SUFFIX}@example.com\",\"password\":\"abc123\"}"

REG_BODY="$(curl -sS -X POST "http://127.0.0.1:${PORT}/auth/register" -H 'Content-Type: application/json' -d "$REGISTER_PAYLOAD")"
[[ "$REG_BODY" == *"userId"* ]]

LOGIN_BODY="$(curl -sS -X POST "http://127.0.0.1:${PORT}/auth/login" -H 'Content-Type: application/json' -d "$LOGIN_PAYLOAD")"
[[ "$LOGIN_BODY" == *"\"authenticated\":true"* ]]

echo "backend-http-smoke: OK"
