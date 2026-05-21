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

if [[ -d "${LXC_PATH}/${LXC_NAME}" ]]; then
  lxc-stop -n "$LXC_NAME" >/dev/null 2>&1 || true
  lxc-destroy -n "$LXC_NAME" >/dev/null 2>&1 || true
fi
lxc-create -n "$LXC_NAME" -t download -- -d debian -r bookworm -a "$ARCH"

if [[ -f "$ROOT_DIR/containers/lxc/portal-captive.conf" ]]; then
  cp "$ROOT_DIR/containers/lxc/portal-captive.conf" "${LXC_PATH}/${LXC_NAME}/config"
fi

lxc-start -n "$LXC_NAME"
sleep 5

# Runtime only (no Java runtime needed)
lxc-attach -n "$LXC_NAME" -- bash -lc "apt-get update && apt-get install -y mosquitto mosquitto-clients sqlite3 curl"
lxc-attach -n "$LXC_NAME" -- bash -lc "mkdir -p /opt/portal-captive-small /opt/portal-captive-small/data"
rsync -a --delete "$ROOT_DIR/" "${LXC_PATH}/${LXC_NAME}/rootfs/opt/portal-captive-small/"
lxc-attach -n "$LXC_NAME" -- bash -lc "mkdir -p /opt/portal-captive-small/data && touch /opt/portal-captive-small/data/auth-service.db && chmod 775 /opt/portal-captive-small/data && chmod 664 /opt/portal-captive-small/data/auth-service.db"

lxc-attach -n "$LXC_NAME" -- bash -lc "test -x /opt/portal-captive-small/backend/bin/db-mqtt-worker-${ARCH}"
lxc-attach -n "$LXC_NAME" -- bash -lc "test -x /opt/portal-captive-small/backend/bin/auth-service-${ARCH}"

lxc-attach -n "$LXC_NAME" -- bash -lc "rm -f /tmp/mosquitto.log /tmp/db-worker.log /tmp/auth-service.log; touch /tmp/mosquitto.log /tmp/db-worker.log /tmp/auth-service.log"
lxc-attach -n "$LXC_NAME" -- bash -lc "rm -f /run/portal-mosquitto.pid /run/portal-db-worker.pid /run/portal-auth.pid"
lxc-attach -n "$LXC_NAME" -- bash -lc "nohup mosquitto -p ${BROKER_PORT} >/tmp/mosquitto.log 2>&1 </dev/null & echo \$! >/run/portal-mosquitto.pid"
lxc-attach -n "$LXC_NAME" -- bash -lc "nohup env MQTT_HOST=127.0.0.1 MQTT_PORT=${BROKER_PORT} SQLITE_DB_PATH=/opt/portal-captive-small/data/auth-service.db DB_USER_REQUEST_TOPIC=portal/db/user/request /opt/portal-captive-small/backend/bin/db-mqtt-worker-${ARCH} >/tmp/db-worker.log 2>&1 </dev/null & echo \$! >/run/portal-db-worker.pid"
lxc-attach -n "$LXC_NAME" -- bash -lc "test -f /opt/portal-captive-small/backend/config/portal-config.toml"
lxc-attach -n "$LXC_NAME" -- bash -lc "cd /opt/portal-captive-small && nohup /opt/portal-captive-small/backend/bin/auth-service-${ARCH} backend/config/portal-config.toml >/tmp/auth-service.log 2>&1 </dev/null & echo \$! >/run/portal-auth.pid"

sleep 4

lxc-attach -n "$LXC_NAME" -- bash -lc "kill -0 \$(cat /run/portal-mosquitto.pid) >/dev/null 2>&1"
lxc-attach -n "$LXC_NAME" -- bash -lc "kill -0 \$(cat /run/portal-db-worker.pid) >/dev/null 2>&1"
lxc-attach -n "$LXC_NAME" -- bash -lc "kill -0 \$(cat /run/portal-auth.pid) >/dev/null 2>&1"

HEALTH_OK=0
for _ in $(seq 1 20); do
  if lxc-attach -n "$LXC_NAME" -- curl -sS http://127.0.0.1:8080/health >/dev/null 2>&1; then
    HEALTH_OK=1
    break
  fi
  sleep 1
done
if [[ "$HEALTH_OK" -ne 1 ]]; then
  echo "Healthcheck falló, mostrando logs:"
  lxc-attach -n "$LXC_NAME" -- bash -lc "echo '--- processes'; ps -ef | grep -E 'mosquitto|db-mqtt-worker|auth-service' | grep -v grep || true"
  lxc-attach -n "$LXC_NAME" -- bash -lc "echo '--- ss 8080'; ss -ltnp | grep 8080 || true"
  lxc-attach -n "$LXC_NAME" -- bash -lc "echo '--- /tmp/mosquitto.log'; tail -n 120 /tmp/mosquitto.log || true"
  lxc-attach -n "$LXC_NAME" -- bash -lc "echo '--- /tmp/db-worker.log'; tail -n 120 /tmp/db-worker.log || true"
  lxc-attach -n "$LXC_NAME" -- bash -lc "echo '--- /tmp/auth-service.log'; tail -n 120 /tmp/auth-service.log || true"
  exit 1
fi

echo
echo "Instalación directa completada en Raspberry Pi 3B + LXC (${LXC_NAME})"
