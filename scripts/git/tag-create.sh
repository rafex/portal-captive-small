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

TAG="${1:-}"
if [[ -z "$TAG" ]]; then
  echo "Uso: $0 <tag>"
  echo "Ejemplo: $0 v0.1.9"
  exit 1
fi

if [[ ! "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Tag inválido: $TAG (formato esperado: vX.Y.Z)"
  exit 1
fi

if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "El tag ya existe localmente: $TAG"
  exit 1
fi

[[ -f CHANGE.md ]] || { echo "Falta CHANGE.md"; exit 1; }
[[ -f RELEASE.md ]] || { echo "Falta RELEASE.md"; exit 1; }

if grep -Fq "## $TAG" CHANGE.md; then
  echo "CHANGE.md ya contiene sección para $TAG"
  exit 1
fi
if grep -Fq "## $TAG" RELEASE.md; then
  echo "RELEASE.md ya contiene sección para $TAG"
  exit 1
fi

LAST_TAG="$(git describe --tags --abbrev=0 2>/dev/null || true)"
RANGE=""
if [[ -n "$LAST_TAG" ]]; then
  RANGE="${LAST_TAG}..HEAD"
fi

COMMITS_FILE="$(mktemp)"
trap 'rm -f "$COMMITS_FILE" "$tmp_change" "$tmp_release"' EXIT

if [[ -n "$RANGE" ]]; then
  git log --no-merges --pretty='- %s' "$RANGE" >"$COMMITS_FILE"
else
  git log --no-merges --pretty='- %s' -n 15 >"$COMMITS_FILE"
fi

if [[ ! -s "$COMMITS_FILE" ]]; then
  echo "- Release notes pending final summary." >"$COMMITS_FILE"
fi

tmp_change="$(mktemp)"
tmp_release="$(mktemp)"

{
  echo "# CHANGE"
  echo
  echo "## $TAG"
  cat "$COMMITS_FILE"
  echo
  awk 'NR>1 {print}' CHANGE.md
} >"$tmp_change"
mv "$tmp_change" CHANGE.md

{
  echo "# RELEASE"
  echo
  echo "## $TAG"
  echo "- Artifacts publicados para $TAG."
  echo "- Resumen de cambios incluidos:"
  cat "$COMMITS_FILE"
  echo
  awk 'NR>1 {print}' RELEASE.md
} >"$tmp_release"
mv "$tmp_release" RELEASE.md

echo "Notas generadas para $TAG en CHANGE.md y RELEASE.md"
