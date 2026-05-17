#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Uso: $0 <version>"
  exit 1
fi

VERSION="$1"
BASE_URL="https://github.com/rafex/portal-captive-small/releases/download/${VERSION}"

curl -fsSLO "${BASE_URL}/frontend-${VERSION}.tar.gz"
curl -fsSLO "${BASE_URL}/frontend-${VERSION}.tar.gz.sha256"
curl -fsSLO "${BASE_URL}/backend-${VERSION}.tar.gz"
curl -fsSLO "${BASE_URL}/backend-${VERSION}.tar.gz.sha256"

sha256sum -c "frontend-${VERSION}.tar.gz.sha256"
sha256sum -c "backend-${VERSION}.tar.gz.sha256"

tar -xzf "frontend-${VERSION}.tar.gz"
tar -xzf "backend-${VERSION}.tar.gz"

echo "Instalación base completada para ${VERSION}"
