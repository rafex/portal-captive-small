#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

mkdir -p .githooks
if [[ -f .githooks/pre-push ]]; then
  chmod +x .githooks/pre-push
fi

git config core.hooksPath .githooks

echo "Git hooks instalados: core.hooksPath=.githooks"
