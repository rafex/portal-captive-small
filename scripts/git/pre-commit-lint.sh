#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

mapfile -t STAGED < <(git diff --cached --name-only --diff-filter=ACMR)

if [[ ${#STAGED[@]} -eq 0 ]]; then
  echo "[pre-commit] sin archivos staged"
  exit 0
fi

has_match() {
  local regex="$1"
  local f
  for f in "${STAGED[@]}"; do
    if [[ "$f" =~ $regex ]]; then
      return 0
    fi
  done
  return 1
}

echo "[pre-commit] lint/check por archivos staged"

if has_match '\.sh$'; then
  echo "[pre-commit] shellcheck"
  SH_FILES=()
  for f in "${STAGED[@]}"; do
    if [[ "$f" == *.sh && -f "$f" ]]; then
      SH_FILES+=("$f")
    fi
  done
  if [[ ${#SH_FILES[@]} -gt 0 ]]; then
    shellcheck "${SH_FILES[@]}"
  fi
fi

if has_match '^backend/java/.*\.java$'; then
  echo "[pre-commit] java test-compile"
  (
    cd backend/java/portal
    mvn -q -DskipTests test-compile
  )
fi

if has_match '^backend/rust/.*\.rs$'; then
  echo "[pre-commit] rust fmt/check"
  (
    cd backend/rust/database-conector-sqlite
    cargo fmt --all -- --check
    cargo check --quiet
  )
fi

if has_match '^frontend/javascripts/portal/.*\.(js|mjs|cjs|ts|tsx|css|html)$'; then
  echo "[pre-commit] frontend build check"
  (
    cd frontend/javascripts/portal
    npm ci --no-audit --no-fund
    npm run build
  )
fi

echo "[pre-commit] OK"
