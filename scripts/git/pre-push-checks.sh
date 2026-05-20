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

PUSH_LINES=()
while IFS= read -r line; do
  PUSH_LINES+=("$line")
done

echo "[pre-push] quality-local"
"$ROOT_DIR/scripts/git/quality-local.sh"

TAGS=()
for line in "${PUSH_LINES[@]}"; do
  [[ -z "$line" ]] && continue
  local_ref="$(awk '{print $1}' <<<"$line")"
  remote_ref="$(awk '{print $3}' <<<"$line")"

  if [[ "$remote_ref" == refs/tags/* || "$local_ref" == refs/tags/* ]]; then
    ref="${remote_ref#refs/tags/}"
    if [[ "$ref" == "$remote_ref" ]]; then
      ref="${local_ref#refs/tags/}"
    fi
    [[ -n "$ref" ]] && TAGS+=("$ref")
  fi
done

if [[ ${#TAGS[@]} -gt 0 ]]; then
  echo "[pre-push] tag push detectado: ${TAGS[*]}"

  [[ -f CHANGE.md ]] || { echo "Falta CHANGE.md para publicar tag"; exit 1; }
  [[ -f RELEASE.md ]] || { echo "Falta RELEASE.md para publicar tag"; exit 1; }

  for tag in "${TAGS[@]}"; do
    grep -Fq "$tag" CHANGE.md || { echo "CHANGE.md no contiene el tag $tag"; exit 1; }
    grep -Fq "$tag" RELEASE.md || { echo "RELEASE.md no contiene el tag $tag"; exit 1; }
  done
fi

echo "[pre-push] OK"
