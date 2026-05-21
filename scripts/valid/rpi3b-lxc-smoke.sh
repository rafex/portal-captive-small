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
need_cmd lxc-create; need_cmd lxc-start; need_cmd lxc-stop; need_cmd lxc-attach; need_cmd curl; need_cmd rsync

cleanup() { set +e; lxc-stop -n "$LXC_NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

if [[ ! -d "$ROOTFS" ]]; then
  echo "Creando contenedor LXC $LXC_NAME"
  lxc-create -n "$LXC_NAME" -t download -- -d debian -r bookworm -a "$ARCH"
fi

cp "$ROOT_DIR/containers/lxc/portal-captive.conf" "${LXC_PATH}/${LXC_NAME}/config"

lxc-start -n "$LXC_NAME"
sleep 5

lxc-attach -n "$LXC_NAME" -- bash -lc "apt-get update && apt-get install -y mosquitto mosquitto-clients sqlite3 curl"

bash "$ROOT_DIR/scripts/install/rpi3b-lxc-install.sh" "$VERSION"

lxc-attach -n "$LXC_NAME" -- bash -lc "mkdir -p /opt/portal-captive-small /opt/portal-captive-small/data"
rsync -a --delete "/opt/portal-captive-small/" "${ROOTFS}/opt/portal-captive-small/"

lxc-attach -n "$LXC_NAME" -- bash -lc "test -x /opt/portal-captive-small/backend/bin/db-mqtt-worker-${ARCH}"
lxc-attach -n "$LXC_NAME" -- bash -lc "test -x /opt/portal-captive-small/backend/bin/auth-service-${ARCH}"

lxc-attach -n "$LXC_NAME" -- bash -lc "pkill -x mosquitto || true"
lxc-attach -n "$LXC_NAME" -- bash -lc "pkill -f '/opt/portal-captive-small/backend/bin/db-mqtt-worker-${ARCH}' || true"
lxc-attach -n "$LXC_NAME" -- bash -lc "pkill -f '/opt/portal-captive-small/backend/bin/auth-service-${ARCH}' || true"

lxc-attach -n "$LXC_NAME" -- bash -lc "mosquitto -p ${BROKER_PORT} >/tmp/mosquitto.log 2>&1 &"
lxc-attach -n "$LXC_NAME" -- bash -lc "MQTT_HOST=127.0.0.1 MQTT_PORT=${BROKER_PORT} SQLITE_DB_PATH=/opt/portal-captive-small/data/auth-service.db DB_USER_REQUEST_TOPIC=portal/db/user/request /opt/portal-captive-small/backend/bin/db-mqtt-worker-${ARCH} >/tmp/db-worker.log 2>&1 &"
lxc-attach -n "$LXC_NAME" -- bash -lc "cd /opt/portal-captive-small && /opt/portal-captive-small/backend/bin/auth-service-${ARCH} config/portal-config.toml >/tmp/auth-service.log 2>&1 &"

sleep 4
RESP_HEALTH="$(lxc-attach -n "$LXC_NAME" -- curl -sS http://127.0.0.1:8080/health)"
RESP_DB_HEALTH="$(lxc-attach -n "$LXC_NAME" -- curl -sS http://127.0.0.1:8080/health/db-mqtt)"
[[ "$RESP_HEALTH" == *'"status":"ok"'* ]]
[[ "$RESP_DB_HEALTH" == *'"healthy":true'* ]]

echo "rpi3b-lxc-smoke: OK"
