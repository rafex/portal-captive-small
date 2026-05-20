#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

mkdir -p .githooks
[[ -f .githooks/pre-push ]] && chmod +x .githooks/pre-push
[[ -f .githooks/pre-commit ]] && chmod +x .githooks/pre-commit
[[ -f scripts/git/pre-push-checks.sh ]] && chmod +x scripts/git/pre-push-checks.sh
[[ -f scripts/git/pre-commit-lint.sh ]] && chmod +x scripts/git/pre-commit-lint.sh

git config core.hooksPath .githooks

echo "Git hooks instalados: core.hooksPath=.githooks"
