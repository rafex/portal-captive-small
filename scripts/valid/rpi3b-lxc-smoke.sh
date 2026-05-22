#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LXC_NAME="${LXC_NAME:-portal-captive}"
LXC_PATH="${LXC_PATH:-/var/lib/lxc}"
ROOTFS="${ROOTFS:-${LXC_PATH}/${LXC_NAME}/rootfs}"
BROKER_PORT="${BROKER_PORT:-1883}"
VERSION="${VERSION:-v0.1.0}"
ARCH="${ARCH:-arm64}"
CT_IP="${CT_IP:-10.0.3.15}"
CT_GW="${CT_GW:-10.0.3.1}"

need_cmd() { command -v "$1" >/dev/null 2>&1 || { echo "Falta comando: $1"; exit 1; }; }
need_cmd lxc-create; need_cmd lxc-start; need_cmd lxc-stop; need_cmd lxc-destroy; need_cmd lxc-attach; need_cmd curl; need_cmd rsync

dump_debug() {
  set +e
  lxc-attach -n "$LXC_NAME" -- bash -lc "echo '--- processes'; ps -ef | grep -E 'mosquitto|db-mqtt-worker|auth-service|http.server' | grep -v grep || true"
  lxc-attach -n "$LXC_NAME" -- bash -lc "echo '--- ss 80/1883/8080'; ss -ltnp | grep -E ':80|:1883|:8080' || true"
  lxc-attach -n "$LXC_NAME" -- bash -lc "echo '--- /tmp/mosquitto.log'; tail -n 120 /tmp/mosquitto.log || true"
  lxc-attach -n "$LXC_NAME" -- bash -lc "echo '--- /tmp/db-worker.log'; tail -n 120 /tmp/db-worker.log || true"
  lxc-attach -n "$LXC_NAME" -- bash -lc "echo '--- /tmp/auth-service.log'; tail -n 120 /tmp/auth-service.log || true"
  lxc-attach -n "$LXC_NAME" -- bash -lc "echo '--- /tmp/frontend.log'; tail -n 120 /tmp/frontend.log || true"
}

cleanup() { set +e; lxc-stop -n "$LXC_NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

if [[ -d "${LXC_PATH}/${LXC_NAME}" ]]; then
  lxc-stop -n "$LXC_NAME" >/dev/null 2>&1 || true
  lxc-destroy -n "$LXC_NAME" >/dev/null 2>&1 || true
fi
echo "Creando contenedor LXC $LXC_NAME"
lxc-create -n "$LXC_NAME" -t download -- -d debian -r bookworm -a "$ARCH"

cp "$ROOT_DIR/containers/lxc/portal-captive.conf" "${LXC_PATH}/${LXC_NAME}/config"
if grep -q '^lxc.net.0.ipv4.address' "${LXC_PATH}/${LXC_NAME}/config"; then
  sed -i "s|^lxc.net.0.ipv4.address.*|lxc.net.0.ipv4.address = ${CT_IP}/24|" "${LXC_PATH}/${LXC_NAME}/config"
else
  echo "lxc.net.0.ipv4.address = ${CT_IP}/24" >> "${LXC_PATH}/${LXC_NAME}/config"
fi
if grep -q '^lxc.net.0.ipv4.gateway' "${LXC_PATH}/${LXC_NAME}/config"; then
  sed -i "s|^lxc.net.0.ipv4.gateway.*|lxc.net.0.ipv4.gateway = ${CT_GW}|" "${LXC_PATH}/${LXC_NAME}/config"
else
  echo "lxc.net.0.ipv4.gateway = ${CT_GW}" >> "${LXC_PATH}/${LXC_NAME}/config"
fi

lxc-start -n "$LXC_NAME"
sleep 5

lxc-attach -n "$LXC_NAME" -- bash -lc "printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' >/etc/resolv.conf"
if ! lxc-attach -n "$LXC_NAME" -- bash -lc "command -v mosquitto >/dev/null 2>&1 && command -v sqlite3 >/dev/null 2>&1 && command -v curl >/dev/null 2>&1 && command -v python3 >/dev/null 2>&1"; then
  lxc-attach -n "$LXC_NAME" -- bash -lc "apt-get update -o Acquire::Retries=5 -o Acquire::http::Timeout=20 && apt-get install -y --no-install-recommends mosquitto mosquitto-clients sqlite3 curl python3 ca-certificates"
fi

bash "$ROOT_DIR/scripts/install/rpi3b-lxc-install.sh" "$VERSION"

lxc-attach -n "$LXC_NAME" -- bash -lc "mkdir -p /opt/portal-captive-small /opt/portal-captive-small/data"
rsync -a --delete "/opt/portal-captive-small/" "${ROOTFS}/opt/portal-captive-small/"
lxc-attach -n "$LXC_NAME" -- bash -lc "mkdir -p /etc/network && cat >/etc/network/interfaces <<'EOF'
auto lo
iface lo inet loopback

auto eth0
iface eth0 inet static
    address ${CT_IP}/24
    gateway ${CT_GW}
    dns-nameservers 1.1.1.1 8.8.8.8
EOF"
lxc-attach -n "$LXC_NAME" -- bash -lc "mkdir -p /opt/portal-captive-small/data && touch /opt/portal-captive-small/data/auth-service.db && chmod 775 /opt/portal-captive-small/data && chmod 664 /opt/portal-captive-small/data/auth-service.db"

lxc-attach -n "$LXC_NAME" -- bash -lc "test -x /opt/portal-captive-small/backend/bin/db-mqtt-worker-${ARCH}"
lxc-attach -n "$LXC_NAME" -- bash -lc "test -x /opt/portal-captive-small/backend/bin/auth-service-${ARCH}"

lxc-attach -n "$LXC_NAME" -- bash -lc "rm -f /run/portal-mosquitto.pid /run/portal-db-worker.pid /run/portal-auth.pid"
lxc-attach -n "$LXC_NAME" -- bash -lc "setsid mosquitto -p ${BROKER_PORT} >/tmp/mosquitto.log 2>&1 </dev/null & echo \$! >/run/portal-mosquitto.pid"
lxc-attach -n "$LXC_NAME" -- bash -lc "env MQTT_HOST=127.0.0.1 MQTT_PORT=${BROKER_PORT} SQLITE_DB_PATH=/opt/portal-captive-small/data/auth-service.db DB_USER_REQUEST_TOPIC=portal/db/user/request setsid /opt/portal-captive-small/backend/bin/db-mqtt-worker-${ARCH} >/tmp/db-worker.log 2>&1 </dev/null & echo \$! >/run/portal-db-worker.pid"
lxc-attach -n "$LXC_NAME" -- bash -lc "test -f /opt/portal-captive-small/backend/config/portal-config.toml"
lxc-attach -n "$LXC_NAME" -- bash -lc "cd /opt/portal-captive-small && setsid /opt/portal-captive-small/backend/bin/auth-service-${ARCH} backend/config/portal-config.toml >/tmp/auth-service.log 2>&1 </dev/null & echo \$! >/run/portal-auth.pid"
lxc-attach -n "$LXC_NAME" -- bash -lc "FRONT_DIR=''; for d in /opt/portal-captive-small/dist /opt/portal-captive-small/frontend/dist /opt/portal-captive-small/frontend/javascripts/portal/dist; do if [[ -d \"\$d\" ]] && [[ -f \"\$d/index.html\" ]]; then FRONT_DIR=\"\$d\"; break; fi; done; if [[ -n \"\$FRONT_DIR\" ]]; then cd \"\$FRONT_DIR\" && setsid python3 -m http.server 80 >/tmp/frontend.log 2>&1 </dev/null & fi"

sleep 4
lxc-attach -n "$LXC_NAME" -- bash -lc "kill -0 \$(cat /run/portal-mosquitto.pid) >/dev/null 2>&1"
RESP_HEALTH="$(lxc-attach -n "$LXC_NAME" -- curl -sS http://127.0.0.1:8080/health)"
RESP_DB_HEALTH="$(lxc-attach -n "$LXC_NAME" -- curl -sS http://127.0.0.1:8080/health/db-mqtt)"
[[ "$RESP_HEALTH" == *'"status":"ok"'* ]]
[[ "$RESP_DB_HEALTH" == *'"healthy":true'* ]]

echo "rpi3b-lxc-smoke: OK"
