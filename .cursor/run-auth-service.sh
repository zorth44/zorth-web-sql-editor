#!/usr/bin/env bash
# Temporary local auth service (port 8090). Local development only.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/auth-service"

export AUTH_SERVICE_INTERNAL_KEY="${AUTH_SERVICE_INTERNAL_KEY:-local-sql-editor-key}"
export AUTH_SERVICE_ALLOWED_ORIGINS="${AUTH_SERVICE_ALLOWED_ORIGINS:-http://localhost:5173}"

exec node server.js
