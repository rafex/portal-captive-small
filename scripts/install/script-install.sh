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

curl -fsSLO "${BASE_URL}/frontend-${VER_NO_V}.tar.gz"
curl -fsSLO "${BASE_URL}/frontend-${VER_NO_V}.tar.gz.sha256"
curl -fsSLO "${BASE_URL}/backend-${VER_NO_V}-${ARCH}.tar.gz"
curl -fsSLO "${BASE_URL}/backend-${VER_NO_V}-${ARCH}.tar.gz.sha256"

sha256sum -c "frontend-${VER_NO_V}.tar.gz.sha256"
sha256sum -c "backend-${VER_NO_V}-${ARCH}.tar.gz.sha256"

tar -xzf "frontend-${VER_NO_V}.tar.gz"
tar -xzf "backend-${VER_NO_V}-${ARCH}.tar.gz"

echo "Instalación base completada para ${VERSION} (${ARCH})"
