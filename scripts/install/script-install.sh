#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Uso: $0 <version>"
  exit 1
fi

VERSION="$1"
BASE_URL="https://github.com/rafex/portal-captive-small/releases/download/${VERSION}"
ARCH="${ARCH:-arm64}"
VER_NO_V="${VERSION#v}"

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

curl -fsSLO "${BASE_URL}/backend-${VER_NO_V}-${ARCH}.tar.gz"
curl -fsSLO "${BASE_URL}/backend-${VER_NO_V}-${ARCH}.tar.gz.sha256"

verify_checksum "backend-${VER_NO_V}-${ARCH}.tar.gz" "backend-${VER_NO_V}-${ARCH}.tar.gz.sha256"

tar -xzf "backend-${VER_NO_V}-${ARCH}.tar.gz"

echo "Instalación base completada para ${VERSION} (${ARCH})"
