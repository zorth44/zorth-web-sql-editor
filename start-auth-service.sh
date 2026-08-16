#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT/auth-service"

export AUTH_SERVICE_PORT="${AUTH_SERVICE_PORT:-8090}"
export AUTH_SERVICE_INTERNAL_KEY="${AUTH_SERVICE_INTERNAL_KEY:-local-sql-editor-key}"
export AUTH_SERVICE_TOKEN_TTL_SECONDS="${AUTH_SERVICE_TOKEN_TTL_SECONDS:-28800}"
export AUTH_SERVICE_ALLOWED_ORIGINS="${AUTH_SERVICE_ALLOWED_ORIGINS:-http://localhost:5173}"
export AUTH_SERVICE_PRODUCT_ID="${AUTH_SERVICE_PRODUCT_ID:-local-product}"
export AUTH_SERVICE_PRODUCT_NAME="${AUTH_SERVICE_PRODUCT_NAME:-本地开发产品}"

if ! command -v node >/dev/null 2>&1; then
  echo "需要 Node.js 20+ 才能启动 auth-service" >&2
  exit 1
fi

exec node server.js
