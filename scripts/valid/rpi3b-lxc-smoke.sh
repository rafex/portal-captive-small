#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LXC_NAME="${LXC_NAME:-portal-captive}"
LXC_PATH="${LXC_PATH:-/var/lib/lxc}"
ROOTFS="${ROOTFS:-${LXC_PATH}/${LXC_NAME}/rootfs}"
HOST_HTTP_PORT="${HOST_HTTP_PORT:-18099}"
BROKER_PORT="${BROKER_PORT:-1883}"

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "Falta comando: $1"; exit 1; }
}

need_cmd lxc-create
need_cmd lxc-start
need_cmd lxc-stop
need_cmd lxc-attach
need_cmd mosquitto
need_cmd sqlite3
need_cmd curl

cleanup() {
  set +e
  lxc-stop -n "$LXC_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

if [[ ! -d "$ROOTFS" ]]; then
  echo "Creando contenedor LXC $LXC_NAME"
  lxc-create -n "$LXC_NAME" -t download -- -d debian -r bookworm -a armhf
fi

cp "$ROOT_DIR/containers/lxc/portal-captive.conf" "${LXC_PATH}/${LXC_NAME}/config"

lxc-start -n "$LXC_NAME"
sleep 5

lxc-attach -n "$LXC_NAME" -- bash -lc "apt-get update && apt-get install -y openjdk-21-jre-headless mosquitto mosquitto-clients sqlite3"

lxc-attach -n "$LXC_NAME" -- bash -lc "mkdir -p /opt/portal-captive-small"

# En un entorno real de release, aquí se usaría scripts/install/rpi3b-lxc-install.sh dentro del contenedor.
# Para smoke local, copiamos workspace actual al contenedor.
rsync -a --delete "$ROOT_DIR/" "${ROOTFS}/opt/portal-captive-small/"

lxc-attach -n "$LXC_NAME" -- bash -lc "cd /opt/portal-captive-small/backend/rust/database-conector-sqlite && cargo build --release"
lxc-attach -n "$LXC_NAME" -- bash -lc "cd /opt/portal-captive-small/backend/java/portal && mvn -q -DskipTests package"

lxc-attach -n "$LXC_NAME" -- bash -lc "mosquitto -p ${BROKER_PORT} >/tmp/mosquitto.log 2>&1 &"
lxc-attach -n "$LXC_NAME" -- bash -lc "cd /opt/portal-captive-small/backend/rust/database-conector-sqlite && MQTT_HOST=127.0.0.1 MQTT_PORT=${BROKER_PORT} SQLITE_DB_PATH=/opt/portal-captive-small/data/auth-service.db DB_USER_REQUEST_TOPIC=portal/db/user/request target/release/db-mqtt-worker >/tmp/db-worker.log 2>&1 &"
lxc-attach -n "$LXC_NAME" -- bash -lc "cd /opt/portal-captive-small && java -cp backend/java/portal/services/auth-service/target/auth-service-0.1.0.jar com.portal.auth.AuthApplication config/portal-config.toml >/tmp/auth-service.log 2>&1 &"

sleep 4

RESP_HEALTH="$(lxc-attach -n "$LXC_NAME" -- curl -sS http://127.0.0.1:8080/health)"
RESP_DB_HEALTH="$(lxc-attach -n "$LXC_NAME" -- curl -sS http://127.0.0.1:8080/health/db-mqtt)"

[[ "$RESP_HEALTH" == *'"status":"ok"'* ]]
[[ "$RESP_DB_HEALTH" == *'"healthy":true'* ]]

echo "rpi3b-lxc-smoke: OK"
