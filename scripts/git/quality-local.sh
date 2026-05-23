#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${PORTAL_BASH_BOOTSTRAPPED:-}" ]]; then
  export PORTAL_BASH_BOOTSTRAPPED=1
  need_major=5
  current_major="${BASH_VERSINFO:-0}"
  if [[ "$current_major" -lt "$need_major" ]]; then
    if [[ "$(uname -s)" == "Darwin" ]]; then
      for cand in /opt/homebrew/bin/bash /usr/local/bin/bash; do
        if [[ -x "$cand" ]]; then
          exec "$cand" "$0" "$@"
        fi
      done
    else
      for cand in /usr/bin/bash /bin/bash; do
        if [[ -x "$cand" ]]; then
          exec "$cand" "$0" "$@"
        fi
      done
    fi
  fi
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

echo "[quality-local] make validate"
make validate

echo "[quality-local] rust tests"
(
  cd backend/rust/database-conector-sqlite
  cargo test --quiet
)

echo "[quality-local] full test suite"
make test

echo "[quality-local] shellcheck"
find scripts -type f -name '*.sh' -print0 | xargs -0 -r shellcheck

echo "[quality-local] lizard (if installed)"
if command -v lizard >/dev/null 2>&1; then
  lizard backend scripts -x "**/target/**" -x "**/node_modules/**" -x "**/dist/**" -x "**/.git/**" -C 25
else
  echo "lizard no está instalado; omitiendo complejidad ciclomática local"
fi

echo "[quality-local] OWASP se ejecuta en workflow separado: security-scan"

echo "[quality-local] OK"
