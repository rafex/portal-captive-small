#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Este script debe ejecutarse como root (sudo)."
  exit 1
fi

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
  echo "Uso: $0 <version-tag>"
  echo "Ejemplo: $0 v0.1.0"
  exit 1
fi

ROOT_DIR="/opt/portal-captive-small"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LXC_NAME="${LXC_NAME:-portal-captive}"
LXC_PATH="${LXC_PATH:-/var/lib/lxc}"
BROKER_PORT="${BROKER_PORT:-1883}"
ARCH="${ARCH:-arm64}"

"${SCRIPT_DIR}/rpi3b-lxc-install.sh" "$VERSION"

if [[ ! -d "${LXC_PATH}/${LXC_NAME}/rootfs" ]]; then
  lxc-create -n "$LXC_NAME" -t download -- -d debian -r bookworm -a "$ARCH"
fi

if [[ -f "$ROOT_DIR/containers/lxc/portal-captive.conf" ]]; then
  cp "$ROOT_DIR/containers/lxc/portal-captive.conf" "${LXC_PATH}/${LXC_NAME}/config"
fi

lxc-start -n "$LXC_NAME" || true
sleep 5

# Runtime only (no Java runtime needed)
lxc-attach -n "$LXC_NAME" -- bash -lc "apt-get update && apt-get install -y mosquitto mosquitto-clients sqlite3 curl"
lxc-attach -n "$LXC_NAME" -- bash -lc "mkdir -p /opt/portal-captive-small /opt/portal-captive-small/data"
rsync -a --delete "$ROOT_DIR/" "${LXC_PATH}/${LXC_NAME}/rootfs/opt/portal-captive-small/"

lxc-attach -n "$LXC_NAME" -- bash -lc "test -x /opt/portal-captive-small/backend/bin/db-mqtt-worker-${ARCH}"
lxc-attach -n "$LXC_NAME" -- bash -lc "test -x /opt/portal-captive-small/backend/bin/auth-service-${ARCH}"

lxc-attach -n "$LXC_NAME" -- bash -lc "pkill -x mosquitto || true"
lxc-attach -n "$LXC_NAME" -- bash -lc "self=\$\$; for p in \$(pgrep -f '/opt/portal-captive-small/backend/bin/db-mqtt-worker-${ARCH}' || true); do [[ \"\$p\" == \"\$self\" ]] && continue; kill \"\$p\" || true; done"
lxc-attach -n "$LXC_NAME" -- bash -lc "self=\$\$; for p in \$(pgrep -f '/opt/portal-captive-small/backend/bin/auth-service-${ARCH}' || true); do [[ \"\$p\" == \"\$self\" ]] && continue; kill \"\$p\" || true; done"

lxc-attach -n "$LXC_NAME" -- bash -lc "mosquitto -p ${BROKER_PORT} >/tmp/mosquitto.log 2>&1 &"
lxc-attach -n "$LXC_NAME" -- bash -lc "MQTT_HOST=127.0.0.1 MQTT_PORT=${BROKER_PORT} SQLITE_DB_PATH=/opt/portal-captive-small/data/auth-service.db DB_USER_REQUEST_TOPIC=portal/db/user/request /opt/portal-captive-small/backend/bin/db-mqtt-worker-${ARCH} >/tmp/db-worker.log 2>&1 &"
lxc-attach -n "$LXC_NAME" -- bash -lc "cd /opt/portal-captive-small && /opt/portal-captive-small/backend/bin/auth-service-${ARCH} config/portal-config.toml >/tmp/auth-service.log 2>&1 &"

sleep 4
lxc-attach -n "$LXC_NAME" -- curl -sS http://127.0.0.1:8080/health

echo
echo "Instalación directa completada en Raspberry Pi 3B + LXC (${LXC_NAME})"
