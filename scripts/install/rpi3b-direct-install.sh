#!/usr/bin/env bash
set -euo pipefail
trap dump_debug ERR

dump_debug() {
  set +e
  echo "Fallo detectado, diagnóstico LXC:"
  lxc-attach -n "${LXC_NAME:-portal-captive}" -- bash -lc "echo '--- processes'; ps -ef | grep -E 'mosquitto|db-mqtt-worker|auth-service|http.server' | grep -v grep || true"
  lxc-attach -n "${LXC_NAME:-portal-captive}" -- bash -lc "echo '--- ss 80/1883/8080'; ss -ltnp | grep -E ':80|:1883|:8080' || true"
  lxc-attach -n "${LXC_NAME:-portal-captive}" -- bash -lc "echo '--- /tmp/mosquitto.log'; tail -n 120 /tmp/mosquitto.log || true"
  lxc-attach -n "${LXC_NAME:-portal-captive}" -- bash -lc "echo '--- /tmp/db-worker.log'; tail -n 120 /tmp/db-worker.log || true"
  lxc-attach -n "${LXC_NAME:-portal-captive}" -- bash -lc "echo '--- /tmp/auth-service.log'; tail -n 120 /tmp/auth-service.log || true"
  lxc-attach -n "${LXC_NAME:-portal-captive}" -- bash -lc "echo '--- /tmp/frontend.log'; tail -n 120 /tmp/frontend.log || true"
}

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
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
DIST_DIR="${DIST_DIR:-/tmp/portal-captive-small-dist}"
BASE_URL="https://github.com/rafex/portal-captive-small/releases/download/${VERSION}"
LXC_NAME="${LXC_NAME:-portal-captive}"
LXC_PATH="${LXC_PATH:-/var/lib/lxc}"
BROKER_PORT="${BROKER_PORT:-1883}"
ARCH="${ARCH:-arm64}"
CT_IP="${CT_IP:-10.0.3.15}"
CT_GW="${CT_GW:-10.0.3.1}"
USE_RELEASE_LXC_IMAGE="${USE_RELEASE_LXC_IMAGE:-1}"

verify_checksum() {
  local file="$1"
  local checksum_file="$2"
  local expected actual
  expected="$(awk '{print $1}' "$checksum_file" | head -n1)"
  actual="$(sha256sum "$file" | awk '{print $1}')"
  [[ -n "$expected" && "$expected" == "$actual" ]]
}

"${SCRIPT_DIR}/rpi3b-lxc-install.sh" "$VERSION"

if [[ -d "${LXC_PATH}/${LXC_NAME}" ]]; then
  lxc-stop -n "$LXC_NAME" >/dev/null 2>&1 || true
  lxc-destroy -n "$LXC_NAME" >/dev/null 2>&1 || true
fi

PREBUILT_OK=0
if [[ "$USE_RELEASE_LXC_IMAGE" == "1" ]]; then
  mkdir -p "$DIST_DIR"
  cd "$DIST_DIR"
  if curl -fsSLO "${BASE_URL}/lxc-image-${VERSION#v}-${ARCH}.tar.gz" \
    && curl -fsSLO "${BASE_URL}/lxc-image-${VERSION#v}-${ARCH}.tar.gz.sha256" \
    && verify_checksum "lxc-image-${VERSION#v}-${ARCH}.tar.gz" "lxc-image-${VERSION#v}-${ARCH}.tar.gz.sha256"; then
    mkdir -p "$LXC_PATH"
    tar -xzf "lxc-image-${VERSION#v}-${ARCH}.tar.gz" -C "$LXC_PATH"
    PREBUILT_OK=1
  fi
fi

if [[ "$PREBUILT_OK" -ne 1 ]]; then
  lxc-create -n "$LXC_NAME" -t download -- -d debian -r bookworm -a "$ARCH"
fi

if [[ -f "$REPO_ROOT/containers/lxc/portal-captive.conf" ]]; then
  cp "$REPO_ROOT/containers/lxc/portal-captive.conf" "${LXC_PATH}/${LXC_NAME}/config"
elif [[ -f "$ROOT_DIR/containers/lxc/portal-captive.conf" ]]; then
  cp "$ROOT_DIR/containers/lxc/portal-captive.conf" "${LXC_PATH}/${LXC_NAME}/config"
fi

# Enforce static IPv4 in effective LXC config (defensive against older release artifacts).
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

# Runtime deps: prefer preinstalled in release LXC image; fallback to apt with retries.
lxc-attach -n "$LXC_NAME" -- bash -lc "printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' >/etc/resolv.conf"
if ! lxc-attach -n "$LXC_NAME" -- bash -lc "command -v mosquitto >/dev/null 2>&1 && command -v sqlite3 >/dev/null 2>&1 && command -v curl >/dev/null 2>&1 && command -v python3 >/dev/null 2>&1"; then
  lxc-attach -n "$LXC_NAME" -- bash -lc "apt-get update -o Acquire::Retries=5 -o Acquire::http::Timeout=20 && apt-get install -y --no-install-recommends mosquitto mosquitto-clients sqlite3 curl python3 ca-certificates"
fi
lxc-attach -n "$LXC_NAME" -- bash -lc "mkdir -p /opt/portal-captive-small /opt/portal-captive-small/data"
rsync -a --delete "$ROOT_DIR/" "${LXC_PATH}/${LXC_NAME}/rootfs/opt/portal-captive-small/"
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

lxc-attach -n "$LXC_NAME" -- bash -lc "rm -f /tmp/mosquitto.log /tmp/db-worker.log /tmp/auth-service.log /tmp/frontend.log; touch /tmp/mosquitto.log /tmp/db-worker.log /tmp/auth-service.log /tmp/frontend.log"
lxc-attach -n "$LXC_NAME" -- bash -lc "rm -f /run/portal-mosquitto.pid /run/portal-db-worker.pid /run/portal-auth.pid /run/portal-frontend.pid"
lxc-attach -n "$LXC_NAME" -- bash -lc "if mosquitto_pub -h 127.0.0.1 -p ${BROKER_PORT} -t portal/health -n >/dev/null 2>&1; then echo 0 >/run/portal-mosquitto.pid; else nohup mosquitto -p ${BROKER_PORT} >/tmp/mosquitto.log 2>&1 </dev/null & echo \$! >/run/portal-mosquitto.pid; fi"
# Wait broker readiness to avoid race before worker startup.
lxc-attach -n "$LXC_NAME" -- bash -lc "for i in \$(seq 1 20); do if mosquitto_pub -h 127.0.0.1 -p ${BROKER_PORT} -t portal/health -n >/dev/null 2>&1; then exit 0; fi; sleep 1; done; exit 1"
lxc-attach -n "$LXC_NAME" -- bash -lc "nohup env MQTT_HOST=127.0.0.1 MQTT_PORT=${BROKER_PORT} SQLITE_DB_PATH=/opt/portal-captive-small/data/auth-service.db DB_USER_REQUEST_TOPIC=portal/db/user/request /opt/portal-captive-small/backend/bin/db-mqtt-worker-${ARCH} >/tmp/db-worker.log 2>&1 </dev/null & echo \$! >/run/portal-db-worker.pid"
lxc-attach -n "$LXC_NAME" -- bash -lc "test -f /opt/portal-captive-small/backend/config/portal-config.toml"
lxc-attach -n "$LXC_NAME" -- bash -lc "cd /opt/portal-captive-small && nohup /opt/portal-captive-small/backend/bin/auth-service-${ARCH} backend/config/portal-config.toml >/tmp/auth-service.log 2>&1 </dev/null & echo \$! >/run/portal-auth.pid"
lxc-attach -n "$LXC_NAME" -- bash -lc "FRONT_DIR=''; for d in /opt/portal-captive-small/dist /opt/portal-captive-small/frontend/dist /opt/portal-captive-small/frontend/javascripts/portal/dist; do if [[ -d \"\$d\" ]] && [[ -f \"\$d/index.html\" ]]; then FRONT_DIR=\"\$d\"; break; fi; done; if [[ -n \"\$FRONT_DIR\" ]]; then cd \"\$FRONT_DIR\" && nohup python3 -m http.server 80 >/tmp/frontend.log 2>&1 </dev/null & echo \$! >/run/portal-frontend.pid; else echo 'frontend dist no encontrado; omitiendo server :80'; fi"

sleep 4

lxc-attach -n "$LXC_NAME" -- bash -lc "if [[ \"\$(cat /run/portal-mosquitto.pid)\" == \"0\" ]]; then mosquitto_pub -h 127.0.0.1 -p ${BROKER_PORT} -t portal/health -n >/dev/null 2>&1; else kill -0 \$(cat /run/portal-mosquitto.pid) >/dev/null 2>&1; fi"
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
  echo "Healthcheck falló"
  dump_debug
  exit 1
fi

lxc-attach -n "$LXC_NAME" -- bash -lc "if [[ -f /run/portal-frontend.pid ]]; then kill -0 \$(cat /run/portal-frontend.pid) >/dev/null 2>&1; fi"

echo
echo "Instalación directa completada en Raspberry Pi 3B + LXC (${LXC_NAME})"
