#!/usr/bin/env bash
set -euo pipefail

BROKER_PORT="${BROKER_PORT:-1883}"
ARCH="${ARCH:-arm64}"
ROOT_DIR="${ROOT_DIR:-/opt/portal-captive-small}"
DATA_DB="${DATA_DB:-${ROOT_DIR}/data/auth-service.db}"
REQ_TOPIC="${REQ_TOPIC:-portal/db/user/request}"
USER_TTL_SECONDS="${USER_TTL_SECONDS:-3600}"
AUTH_JAVA_NATIVE_ACCESS_OPTS="${AUTH_JAVA_NATIVE_ACCESS_OPTS:---enable-native-access=ALL-UNNAMED}"

if [[ -f "${ROOT_DIR}/backend/config/portal-config.toml" ]]; then
  CFG_TTL="$(awk '
    /^\[repository\]$/ {in_repo=1; next}
    /^\[/ {in_repo=0}
    in_repo && $1=="users_ttl_seconds" {
      gsub(/ /, "", $0);
      split($0, a, "=");
      print a[2];
      exit
    }
  ' "${ROOT_DIR}/backend/config/portal-config.toml")"
  if [[ -n "${CFG_TTL}" ]]; then
    USER_TTL_SECONDS="${CFG_TTL}"
  fi
fi

mkdir -p /tmp /run "${ROOT_DIR}/data"
rm -f /tmp/mosquitto.log /tmp/db-worker.log /tmp/auth-service.log
: > /tmp/mosquitto.log
: > /tmp/db-worker.log
: > /tmp/auth-service.log
rm -f /run/portal-mosquitto.pid /run/portal-db-worker.pid /run/portal-auth.pid

if mosquitto_pub -h 127.0.0.1 -p "${BROKER_PORT}" -t portal/health -n >/dev/null 2>&1; then
  echo 0 >/run/portal-mosquitto.pid
else
  nohup mosquitto -p "${BROKER_PORT}" >/tmp/mosquitto.log 2>&1 </dev/null &
  echo $! >/run/portal-mosquitto.pid
fi

for _ in $(seq 1 20); do
  if mosquitto_pub -h 127.0.0.1 -p "${BROKER_PORT}" -t portal/health -n >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
mosquitto_pub -h 127.0.0.1 -p "${BROKER_PORT}" -t portal/health -n >/dev/null 2>&1

nohup env MQTT_HOST=127.0.0.1 MQTT_PORT="${BROKER_PORT}" SQLITE_DB_PATH="${DATA_DB}" DB_USER_REQUEST_TOPIC="${REQ_TOPIC}" DB_USER_TTL_SECONDS="${USER_TTL_SECONDS}" "${ROOT_DIR}/backend/bin/db-mqtt-worker-${ARCH}" >/tmp/db-worker.log 2>&1 </dev/null &
echo $! >/run/portal-db-worker.pid

cd "${ROOT_DIR}"
nohup env JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} ${AUTH_JAVA_NATIVE_ACCESS_OPTS}" "${ROOT_DIR}/backend/bin/auth-service-${ARCH}" backend/config/portal-config.toml >/tmp/auth-service.log 2>&1 </dev/null &
echo $! >/run/portal-auth.pid

sleep 2
[[ "$(cat /run/portal-mosquitto.pid)" = "0" ]] || kill -0 "$(cat /run/portal-mosquitto.pid)"
kill -0 "$(cat /run/portal-db-worker.pid)"
kill -0 "$(cat /run/portal-auth.pid)"
