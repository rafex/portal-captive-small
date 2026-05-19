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
LXC_NAME="${LXC_NAME:-portal-captive}"
LXC_PATH="${LXC_PATH:-/var/lib/lxc}"
BROKER_PORT="${BROKER_PORT:-1883}"

/opt/portal-captive-small/scripts/install/rpi3b-lxc-install.sh "$VERSION" || true

if [[ ! -d "$ROOT_DIR" ]]; then
  mkdir -p "$ROOT_DIR"
fi

if [[ ! -d "${LXC_PATH}/${LXC_NAME}/rootfs" ]]; then
  lxc-create -n "$LXC_NAME" -t download -- -d debian -r bookworm -a armhf
fi

if [[ -f "$ROOT_DIR/containers/lxc/portal-captive.conf" ]]; then
  cp "$ROOT_DIR/containers/lxc/portal-captive.conf" "${LXC_PATH}/${LXC_NAME}/config"
fi

lxc-start -n "$LXC_NAME" || true
sleep 5

lxc-attach -n "$LXC_NAME" -- bash -lc "apt-get update && apt-get install -y openjdk-21-jre-headless mosquitto mosquitto-clients sqlite3"
lxc-attach -n "$LXC_NAME" -- bash -lc "mkdir -p /opt/portal-captive-small"

rsync -a --delete "$ROOT_DIR/" "${LXC_PATH}/${LXC_NAME}/rootfs/opt/portal-captive-small/"

lxc-attach -n "$LXC_NAME" -- bash -lc "cd /opt/portal-captive-small/backend/rust/database-conector-sqlite && cargo build --release"
lxc-attach -n "$LXC_NAME" -- bash -lc "cd /opt/portal-captive-small/backend/java/portal && mvn -q -DskipTests package"

lxc-attach -n "$LXC_NAME" -- bash -lc "pkill mosquitto || true; mosquitto -p ${BROKER_PORT} >/tmp/mosquitto.log 2>&1 &"
lxc-attach -n "$LXC_NAME" -- bash -lc "pkill db-mqtt-worker || true; cd /opt/portal-captive-small/backend/rust/database-conector-sqlite && MQTT_HOST=127.0.0.1 MQTT_PORT=${BROKER_PORT} SQLITE_DB_PATH=/opt/portal-captive-small/data/auth-service.db DB_USER_REQUEST_TOPIC=portal/db/user/request target/release/db-mqtt-worker >/tmp/db-worker.log 2>&1 &"
lxc-attach -n "$LXC_NAME" -- bash -lc "pkill -f com.portal.auth.AuthApplication || true; cd /opt/portal-captive-small && java -cp backend/java/portal/services/auth-service/target/auth-service-0.1.0.jar com.portal.auth.AuthApplication config/portal-config.toml >/tmp/auth-service.log 2>&1 &"

sleep 4
lxc-attach -n "$LXC_NAME" -- curl -sS http://127.0.0.1:8080/health

echo
echo "Instalación directa completada en Raspberry Pi 3B + LXC (${LXC_NAME})"
