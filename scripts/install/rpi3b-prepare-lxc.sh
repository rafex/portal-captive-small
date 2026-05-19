#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Este script debe ejecutarse como root (sudo)."
  exit 1
fi

KEEP_IMAGES_BACKUP="${KEEP_IMAGES_BACKUP:-false}"

log() { echo "[prepare-lxc] $*"; }

stop_disable_service() {
  local svc="$1"
  if systemctl list-unit-files | grep -q "^${svc}"; then
    systemctl stop "$svc" >/dev/null 2>&1 || true
    systemctl disable "$svc" >/dev/null 2>&1 || true
    systemctl mask "$svc" >/dev/null 2>&1 || true
    log "Servicio ${svc} detenido/deshabilitado"
  fi
}

remove_path() {
  local p="$1"
  if [[ -e "$p" ]]; then
    rm -rf "$p"
    log "Eliminado: $p"
  fi
}

backup_images_if_requested() {
  if [[ "$KEEP_IMAGES_BACKUP" != "true" ]]; then
    return
  fi

  mkdir -p /var/backups/container-artifacts

  if command -v docker >/dev/null 2>&1; then
    docker image ls --format '{{.Repository}}:{{.Tag}}' | grep -v '^<none>' | while read -r img; do
      safe_name="$(echo "$img" | tr '/:' '__')"
      docker save "$img" -o "/var/backups/container-artifacts/docker_${safe_name}.tar" || true
    done
  fi

  if command -v podman >/dev/null 2>&1; then
    podman image ls --format '{{.Repository}}:{{.Tag}}' | grep -v '^<none>' | while read -r img; do
      safe_name="$(echo "$img" | tr '/:' '__')"
      podman save "$img" -o "/var/backups/container-artifacts/podman_${safe_name}.tar" || true
    done
  fi

  log "Backup opcional completado en /var/backups/container-artifacts"
}

log "Inicio limpieza Docker/Podman para preparar LXC"
backup_images_if_requested

# Docker cleanup
if command -v docker >/dev/null 2>&1; then
  docker ps -aq | xargs -r docker rm -f || true
  docker volume ls -q | xargs -r docker volume rm -f || true
  docker network prune -f || true
  docker system prune -a -f --volumes || true
  log "Docker runtime limpiado"
fi

# Podman cleanup
if command -v podman >/dev/null 2>&1; then
  podman ps -aq | xargs -r podman rm -f || true
  podman volume ls -q | xargs -r podman volume rm -f || true
  podman network prune -f || true
  podman system prune -a -f || true
  log "Podman runtime limpiado"
fi

# Stop and disable services
stop_disable_service docker.service
stop_disable_service docker.socket
stop_disable_service containerd.service
stop_disable_service podman.service
stop_disable_service podman.socket

# Remove packages
apt-get update -y
apt-get purge -y docker docker.io docker-ce docker-ce-cli docker-buildx-plugin docker-compose-plugin containerd runc podman podman-docker || true
apt-get autoremove -y --purge || true
apt-get autoclean -y || true

# Remove known data paths
remove_path /var/lib/docker
remove_path /var/lib/containerd
remove_path /etc/docker
remove_path /run/docker
remove_path /var/run/docker.sock
remove_path /var/lib/containers
remove_path /etc/containers
remove_path /run/containers
remove_path /usr/local/bin/docker-compose

# Ensure LXC base packages
apt-get install -y lxc lxc-templates uidmap bridge-utils debootstrap rsync curl sqlite3 mosquitto mosquitto-clients

log "Host preparado para LXC. Reinicio recomendado."
