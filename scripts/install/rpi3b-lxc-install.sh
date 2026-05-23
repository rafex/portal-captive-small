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
ARCH="${ARCH:-arm64}"

verify_checksum() {
  local file="$1"
  local checksum_file="$2"
  local expected actual
  expected="$(awk '{print $1}' "$checksum_file" | head -n1)"
  actual="$(sha256sum "$file" | awk '{print $1}')"
  if [[ -z "$expected" || "$expected" != "$actual" ]]; then
    echo "Checksum inválido para $file"
    echo "Esperado: $expected"
    echo "Actual:   $actual"
    exit 1
  fi
}

mkdir -p "$DIST_DIR" "$INSTALL_ROOT"
cd "$DIST_DIR"

curl -fsSLO "${BASE_URL}/backend-${VERSION#v}-${ARCH}.tar.gz"
curl -fsSLO "${BASE_URL}/backend-${VERSION#v}-${ARCH}.tar.gz.sha256"
curl -fsSLO "${BASE_URL}/script-install.sh"

verify_checksum "backend-${VERSION#v}-${ARCH}.tar.gz" "backend-${VERSION#v}-${ARCH}.tar.gz.sha256"

tar -xzf "backend-${VERSION#v}-${ARCH}.tar.gz" -C "$INSTALL_ROOT"

install -m 0755 "${PWD}/script-install.sh" "$INSTALL_ROOT/script-install.sh"

echo "Instalación completada en $INSTALL_ROOT (arch=${ARCH})"
