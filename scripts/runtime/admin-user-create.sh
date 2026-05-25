#!/usr/bin/env bash
set -euo pipefail

DB_PATH="${DB_PATH:-data/auth-service.db}"
USERNAME="${1:-}"
ROLE="${2:-viewer}"
PASSWORD="${3:-}"

if [[ -z "$USERNAME" || -z "$PASSWORD" ]]; then
  echo "Uso: $0 <username> <role:admin|viewer> <password>"
  exit 1
fi

if [[ "$ROLE" != "admin" && "$ROLE" != "viewer" ]]; then
  echo "role inválido: $ROLE (usa admin|viewer)"
  exit 1
fi

if ! command -v sqlite3 >/dev/null 2>&1; then
  echo "sqlite3 no instalado"
  exit 1
fi

if ! command -v openssl >/dev/null 2>&1; then
  echo "openssl no instalado"
  exit 1
fi

mkdir -p "$(dirname "$DB_PATH")"
touch "$DB_PATH"

SALT="$(openssl rand -base64 16 | tr -d '\n')"
HASH="$(printf '%s:%s' "$PASSWORD" "$SALT" | openssl dgst -sha256 -binary | openssl base64 -A)"
NOW="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

sqlite3 "$DB_PATH" <<SQL
CREATE TABLE IF NOT EXISTS admin_users (
  username TEXT PRIMARY KEY,
  password_hash TEXT NOT NULL,
  password_salt TEXT NOT NULL,
  role TEXT NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
INSERT INTO admin_users (username,password_hash,password_salt,role,enabled,created_at,updated_at)
VALUES ('$USERNAME','$HASH','$SALT','$ROLE',1,'$NOW','$NOW')
ON CONFLICT(username) DO UPDATE SET
  password_hash=excluded.password_hash,
  password_salt=excluded.password_salt,
  role=excluded.role,
  enabled=excluded.enabled,
  updated_at=excluded.updated_at;
SQL

echo "OK: usuario '$USERNAME' con rol '$ROLE' creado/actualizado en $DB_PATH"

