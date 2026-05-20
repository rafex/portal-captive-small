#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if git config --local --get core.hooksPath >/dev/null 2>&1; then
  git config --local --unset core.hooksPath
  echo "Git hooks desinstalados: core.hooksPath removido"
else
  echo "core.hooksPath no estaba configurado"
fi
