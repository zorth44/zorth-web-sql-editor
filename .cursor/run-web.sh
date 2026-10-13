#!/usr/bin/env bash
# Vite dev server (port 5173) in connected mode, proxying /auth-api -> 8090
# and /sql-api -> 8080 so the browser stays same-origin.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/web"

export VITE_ENABLE_API_MOCK="${VITE_ENABLE_API_MOCK:-false}"
export VITE_SQL_API_BASE="${VITE_SQL_API_BASE:-/sql-api}"
export VITE_AUTH_API_BASE="${VITE_AUTH_API_BASE:-/auth-api}"
export VITE_DEV_SQL_PROXY_TARGET="${VITE_DEV_SQL_PROXY_TARGET:-http://127.0.0.1:8080}"
export VITE_DEV_AUTH_PROXY_TARGET="${VITE_DEV_AUTH_PROXY_TARGET:-http://127.0.0.1:8090}"
export VITE_AUTH_PRODUCT_TYPE="${VITE_AUTH_PRODUCT_TYPE:-chinaBank}"
export VITE_AUTH_BRIDGE_ALLOWED_ORIGINS="${VITE_AUTH_BRIDGE_ALLOWED_ORIGINS:-http://localhost:8080}"
export VITE_LEGACY_PORTAL_URL="${VITE_LEGACY_PORTAL_URL:-http://localhost:8080/account/bind}"

exec pnpm dev
