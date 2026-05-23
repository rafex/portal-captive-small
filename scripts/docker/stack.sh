#!/usr/bin/env bash
# scripts/docker/stack.sh
# Lanzador del stack local portal-captive con Podman.
#
# Uso:
#   bash scripts/docker/stack.sh <comando> [opciones]
#
# Comandos:
#   up       Levanta todos los servicios (--build para reconstruir imágenes)
#   down     Para y elimina los contenedores
#   restart  Reinicia todos los servicios
#   logs     Muestra logs en tiempo real (acepta nombre de servicio como arg extra)
#   ps       Lista los contenedores del stack
#   status   Alias de ps
#   clean    Para el stack y elimina volúmenes (datos locales)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${REPO_ROOT}/containers/docker/compose.yaml"
CMD="${1:-help}"

# ── Verificaciones previas ─────────────────────────────────────────────────────
if ! command -v podman &>/dev/null; then
  echo "[stack.sh] ERROR: podman no está instalado." >&2
  exit 1
fi

_ensure_machine() {
  local state
  state="$(podman machine inspect --format '{{.State}}' 2>/dev/null || echo "unknown")"
  if [[ "$state" != "running" ]]; then
    echo "[stack.sh] podman machine no está corriendo (estado: ${state})."
    echo "           Ejecuta: podman machine start"
    exit 1
  fi
}

_compose() {
  podman compose -f "${COMPOSE_FILE}" "$@"
}

cd "${REPO_ROOT}"

# ── Comandos ───────────────────────────────────────────────────────────────────
case "$CMD" in

  up)
    _ensure_machine
    shift
    BUILD_FLAG=""
    for arg in "$@"; do
      [[ "$arg" == "--build" ]] && BUILD_FLAG="--build"
    done
    echo "[stack.sh] Levantando stack..."
    # shellcheck disable=SC2086
    _compose up -d ${BUILD_FLAG}
    echo ""
    echo "[stack.sh] Stack activo. Portal (frontend + API): http://localhost:8080"
    echo "           MQTT broker:                          localhost:1883"
    ;;

  down)
    echo "[stack.sh] Deteniendo stack..."
    _compose down
    ;;

  restart)
    echo "[stack.sh] Reiniciando stack..."
    _compose down
    _compose up -d
    ;;

  logs)
    shift
    SERVICE="${1:-}"
    if [[ -n "$SERVICE" ]]; then
      _compose logs -f "$SERVICE"
    else
      _compose logs -f
    fi
    ;;

  ps|status)
    _compose ps
    ;;

  clean)
    echo "[stack.sh] AVISO: se eliminarán contenedores Y volúmenes de datos."
    read -r -p "¿Continuar? [s/N] " confirm
    if [[ "${confirm,,}" == "s" ]]; then
      _compose down --volumes --remove-orphans
      echo "[stack.sh] Stack y volúmenes eliminados."
    else
      echo "[stack.sh] Cancelado."
    fi
    ;;

  help|--help|-h|*)
    cat <<'EOF'
Uso: bash scripts/docker/stack.sh <comando>

Comandos disponibles:
  up [--build]   Levanta el stack (--build reconstruye imágenes)
  down           Para y elimina los contenedores
  restart        Reinicia todos los servicios
  logs [svc]     Muestra logs en tiempo real (svc opcional: mosquitto|db-mqtt-worker|auth-service|frontend)
  ps / status    Lista los contenedores del stack
  clean          Para el stack y borra los volúmenes de datos
EOF
    ;;

esac
