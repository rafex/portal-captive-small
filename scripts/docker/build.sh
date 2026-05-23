#!/usr/bin/env bash
# scripts/docker/build.sh
# Construye todos los artefactos del proyecto (Java + Rust) y luego
# genera las imágenes de contenedor con Podman.
#
# Uso:
#   bash scripts/docker/build.sh [--skip-code] [--no-cache]
#
# Opciones:
#   --skip-code   Omite la compilación de Java y Rust (usa artefactos ya existentes)
#   --no-cache    Pasa --no-cache a podman build

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${REPO_ROOT}/containers/docker/compose.yaml"
SKIP_CODE=false
NO_CACHE=""

# ── Argumentos ────────────────────────────────────────────────────────────────
for arg in "$@"; do
  case "$arg" in
    --skip-code) SKIP_CODE=true ;;
    --no-cache)  NO_CACHE="--no-cache" ;;
    *) echo "[build.sh] Argumento desconocido: $arg" >&2; exit 1 ;;
  esac
done

# ── Verificaciones previas ─────────────────────────────────────────────────────
if ! command -v podman &>/dev/null; then
  echo "[build.sh] ERROR: podman no está instalado." >&2
  exit 1
fi

if ! podman machine inspect &>/dev/null 2>&1; then
  echo "[build.sh] AVISO: No se detectó podman machine activo."
  echo "           Inicia uno con: podman machine start"
fi

# ── Compilación de código fuente ───────────────────────────────────────────────
if [[ "$SKIP_CODE" == "false" ]]; then
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo " [1/2] Compilando Java + Rust via make build"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  make -C "${REPO_ROOT}" build
else
  echo "[build.sh] --skip-code activo: omitiendo compilación de código fuente."
fi

# ── Build de imágenes de contenedor ────────────────────────────────────────────
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " [2/2] Construyendo imágenes con podman compose"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
cd "${REPO_ROOT}"
# shellcheck disable=SC2086
podman compose -f "${COMPOSE_FILE}" build ${NO_CACHE}

echo ""
echo "[build.sh] Imágenes listas:"
podman images --filter "reference=portal-captive/*" --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}"
