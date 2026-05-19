#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
  echo "Uso: $0 <version-tag>"
  echo "Ejemplo: $0 v0.1.0"
  exit 1
fi

BASE_URL="https://github.com/rafex/portal-captive-small/releases/download/${VERSION}"
INSTALL_ROOT="${INSTALL_ROOT:-/opt/portal-captive-small}"
DIST_DIR="${DIST_DIR:-/tmp/portal-captive-small-dist}"

mkdir -p "$DIST_DIR" "$INSTALL_ROOT"
cd "$DIST_DIR"

curl -fsSLO "${BASE_URL}/frontend-${VERSION#v}.tar.gz"
curl -fsSLO "${BASE_URL}/frontend-${VERSION#v}.tar.gz.sha256"
curl -fsSLO "${BASE_URL}/backend-${VERSION#v}.tar.gz"
curl -fsSLO "${BASE_URL}/backend-${VERSION#v}.tar.gz.sha256"
curl -fsSLO "${BASE_URL}/script-install.sh"

sha256sum -c "frontend-${VERSION#v}.tar.gz.sha256"
sha256sum -c "backend-${VERSION#v}.tar.gz.sha256"

tar -xzf "frontend-${VERSION#v}.tar.gz" -C "$INSTALL_ROOT"
tar -xzf "backend-${VERSION#v}.tar.gz" -C "$INSTALL_ROOT"

install -m 0755 "${PWD}/script-install.sh" "$INSTALL_ROOT/script-install.sh"

echo "Instalación completada en $INSTALL_ROOT"
