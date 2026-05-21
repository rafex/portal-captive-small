#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LXC_NAME="${LXC_NAME:-portal-captive}"
LXC_PATH="${LXC_PATH:-/var/lib/lxc}"
ROOTFS="${ROOTFS:-${LXC_PATH}/${LXC_NAME}/rootfs}"
BROKER_PORT="${BROKER_PORT:-1883}"
VERSION="${VERSION:-v0.1.0}"
ARCH="${ARCH:-arm64}"

need_cmd() { command -v "$1" >/dev/null 2>&1 || { echo "Falta comando: $1"; exit 1; }; }
need_cmd lxc-create; need_cmd lxc-start; need_cmd lxc-stop; need_cmd lxc-destroy; need_cmd lxc-attach; need_cmd curl; need_cmd rsync

cleanup() { set +e; lxc-stop -n "$LXC_NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

if [[ -d "${LXC_PATH}/${LXC_NAME}" ]]; then
  lxc-stop -n "$LXC_NAME" >/dev/null 2>&1 || true
  lxc-destroy -n "$LXC_NAME" >/dev/null 2>&1 || true
fi
echo "Creando contenedor LXC $LXC_NAME"
lxc-create -n "$LXC_NAME" -t download -- -d debian -r bookworm -a "$ARCH"

cp "$ROOT_DIR/containers/lxc/portal-captive.conf" "${LXC_PATH}/${LXC_NAME}/config"

lxc-start -n "$LXC_NAME"
sleep 5

lxc-attach -n "$LXC_NAME" -- bash -lc "apt-get update && apt-get install -y mosquitto mosquitto-clients sqlite3 curl"

bash "$ROOT_DIR/scripts/install/rpi3b-lxc-install.sh" "$VERSION"

lxc-attach -n "$LXC_NAME" -- bash -lc "mkdir -p /opt/portal-captive-small /opt/portal-captive-small/data"
rsync -a --delete "/opt/portal-captive-small/" "${ROOTFS}/opt/portal-captive-small/"
lxc-attach -n "$LXC_NAME" -- bash -lc "mkdir -p /opt/portal-captive-small/data && touch /opt/portal-captive-small/data/auth-service.db && chmod 775 /opt/portal-captive-small/data && chmod 664 /opt/portal-captive-small/data/auth-service.db"

lxc-attach -n "$LXC_NAME" -- bash -lc "test -x /opt/portal-captive-small/backend/bin/db-mqtt-worker-${ARCH}"
lxc-attach -n "$LXC_NAME" -- bash -lc "test -x /opt/portal-captive-small/backend/bin/auth-service-${ARCH}"

lxc-attach -n "$LXC_NAME" -- bash -lc "rm -f /run/portal-mosquitto.pid /run/portal-db-worker.pid /run/portal-auth.pid"
lxc-attach -n "$LXC_NAME" -- bash -lc "if pgrep -x mosquitto >/dev/null 2>&1; then pgrep -xo mosquitto >/run/portal-mosquitto.pid; else nohup mosquitto -p ${BROKER_PORT} >/tmp/mosquitto.log 2>&1 </dev/null & echo \$! >/run/portal-mosquitto.pid; fi"
lxc-attach -n "$LXC_NAME" -- bash -lc "nohup env MQTT_HOST=127.0.0.1 MQTT_PORT=${BROKER_PORT} SQLITE_DB_PATH=/opt/portal-captive-small/data/auth-service.db DB_USER_REQUEST_TOPIC=portal/db/user/request /opt/portal-captive-small/backend/bin/db-mqtt-worker-${ARCH} >/tmp/db-worker.log 2>&1 </dev/null & echo \$! >/run/portal-db-worker.pid"
lxc-attach -n "$LXC_NAME" -- bash -lc "test -f /opt/portal-captive-small/backend/config/portal-config.toml"
lxc-attach -n "$LXC_NAME" -- bash -lc "cd /opt/portal-captive-small && nohup /opt/portal-captive-small/backend/bin/auth-service-${ARCH} backend/config/portal-config.toml >/tmp/auth-service.log 2>&1 </dev/null & echo \$! >/run/portal-auth.pid"

sleep 4
lxc-attach -n "$LXC_NAME" -- bash -lc "kill -0 \$(cat /run/portal-mosquitto.pid) >/dev/null 2>&1"
lxc-attach -n "$LXC_NAME" -- bash -lc "kill -0 \$(cat /run/portal-db-worker.pid) >/dev/null 2>&1"
lxc-attach -n "$LXC_NAME" -- bash -lc "kill -0 \$(cat /run/portal-auth.pid) >/dev/null 2>&1"
RESP_HEALTH="$(lxc-attach -n "$LXC_NAME" -- curl -sS http://127.0.0.1:8080/health)"
RESP_DB_HEALTH="$(lxc-attach -n "$LXC_NAME" -- curl -sS http://127.0.0.1:8080/health/db-mqtt)"
[[ "$RESP_HEALTH" == *'"status":"ok"'* ]]
[[ "$RESP_DB_HEALTH" == *'"healthy":true'* ]]

echo "rpi3b-lxc-smoke: OK"
