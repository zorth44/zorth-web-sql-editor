#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT/web"

usage() {
  cat >&2 <<'EOF'
用法: ./start-web.sh <mock|connected>

  mock        使用前端 MSW 假接口，不依赖 auth-service / SQL service
  connected   关闭 Mock，经 Vite 代理联调本地后端
              /auth-api → 127.0.0.1:8090
              /sql-api  → 127.0.0.1:8080
EOF
  exit 1
}

mode="${1:-}"
case "$mode" in
  mock | --mock)
    export VITE_ENABLE_API_MOCK=true
    export VITE_SQL_API_BASE="${VITE_SQL_API_BASE:-}"
    export VITE_AUTH_API_BASE="${VITE_AUTH_API_BASE:-}"
    echo "启动前端（Mock）: http://localhost:5173"
    ;;
  connected | --connected | live | --live)
    export VITE_ENABLE_API_MOCK=false
    export VITE_SQL_API_BASE="${VITE_SQL_API_BASE:-/sql-api}"
    export VITE_AUTH_API_BASE="${VITE_AUTH_API_BASE:-/auth-api}"
    export VITE_DEV_SQL_PROXY_TARGET="${VITE_DEV_SQL_PROXY_TARGET:-http://127.0.0.1:8080}"
    export VITE_DEV_AUTH_PROXY_TARGET="${VITE_DEV_AUTH_PROXY_TARGET:-http://127.0.0.1:8090}"
    echo "启动前端（联调）: http://localhost:5173"
    echo "请先启动 ./start-auth-service.sh 和 SQL service（8080）"
    ;;
  *)
    usage
    ;;
esac

export VITE_AUTH_PRODUCT_TYPE="${VITE_AUTH_PRODUCT_TYPE:-chinaBank}"
export VITE_AUTH_BRIDGE_ALLOWED_ORIGINS="${VITE_AUTH_BRIDGE_ALLOWED_ORIGINS:-http://localhost:8080}"
export VITE_LEGACY_PORTAL_URL="${VITE_LEGACY_PORTAL_URL:-http://localhost:8080/account/bind}"

if ! command -v node >/dev/null 2>&1; then
  echo "需要 Node.js 20.19+ 才能启动前端" >&2
  exit 1
fi

if ! command -v pnpm >/dev/null 2>&1; then
  echo "需要 pnpm 才能启动前端，可先执行: corepack enable" >&2
  exit 1
fi

if [ ! -d node_modules ]; then
  echo "未找到 node_modules，正在安装依赖..."
  pnpm install
fi

exec pnpm dev
