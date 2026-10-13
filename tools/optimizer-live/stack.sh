#!/usr/bin/env bash
# Start/stop the local SQL optimizer integration stack:
#   auth-service (temporary local authorization service)
#   SQL service   (this repository, optimizer capability enabled)
#   Agent Platform (sibling bddf-agentscope checkout, real model)
#   Web dev server (Vite, API mock disabled)
#
# Usage: stack.sh <up|down|restart|status>
#
# Required environment: MYSQL_HOST MYSQL_PORT MYSQL_USER MYSQL_PASSWORD LLM_API_KEY
# Optional: OPTIMIZER_LIVE_RUNTIME (default /private/tmp/optimizer-live), AGENT_REPO, JAVA_HOME_17,
#           API_PORT/AUTH_PORT/AGENT_PORT/WEB_PORT, OPTIMIZER_USERS, OPTIMIZER_DENIED_USER,
#           OPTIMIZER_PASSWORD, OPTIMIZATION_SCHEDULER_ENABLED, MYSQL_DEMO_DATABASE
#
# This harness is for local acceptance only. It writes keys and runtime state under
# $OPTIMIZER_LIVE_RUNTIME (owner readable) and never touches other databases or processes.
set -euo pipefail

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
RUNTIME=${OPTIMIZER_LIVE_RUNTIME:-/private/tmp/optimizer-live}
AGENT_REPO=${AGENT_REPO:-$HOME/Code/ai/bddf-agentscope}
API_PORT=${API_PORT:-8080}
AUTH_PORT=${AUTH_PORT:-8090}
AGENT_PORT=${AGENT_PORT:-8081}
WEB_PORT=${WEB_PORT:-5173}
OPTIMIZER_USERS=${OPTIMIZER_USERS:-zhangsan,lisi}
OPTIMIZER_DENIED_USER=${OPTIMIZER_DENIED_USER:-wangwu}
OPTIMIZER_PASSWORD=${OPTIMIZER_PASSWORD:-LocalDevPass123!}
OPTIMIZATION_SCHEDULER_ENABLED=${OPTIMIZATION_SCHEDULER_ENABLED:-true}
MYSQL_DEMO_DATABASE=${MYSQL_DEMO_DATABASE:-optimizer_demo}
MYSQL_METADATA_DATABASE=${MYSQL_METADATA_DATABASE:-sqleditor_dev}
INTERNAL_CALLER_KEY_REQUIRED=${SQL_EDITOR_AGENT_INTERNAL_CALLER_KEY_REQUIRED:-true}
KEYS="$RUNTIME/keys.json"
STATE="$RUNTIME/stack.json"
LOGS="$RUNTIME/logs"

die() { echo "error: $*" >&2; exit 1; }
need() { [ -n "${!1:-}" ] || die "$1 is required"; }

resolve_java() {
  if [ -n "${JAVA_HOME_17:-}" ]; then echo "$JAVA_HOME_17"; return; fi
  for candidate in /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
                   /opt/homebrew/Cellar/openjdk@17/*/libexec/openjdk.jdk/Contents/Home \
                   /usr/lib/jvm/java-17-openjdk*; do
    [ -x "$candidate/bin/java" ] && { echo "$candidate"; return; }
  done
  die "JDK 17 not found; set JAVA_HOME_17"
}

user_id() { printf 'local:%s' "$1" | shasum -a 256 | cut -c1-16; }

generate_keys() {
  [ -f "$KEYS" ] && return
  mkdir -p "$RUNTIME"
  python3 - "$KEYS" "$OPTIMIZER_USERS" "$OPTIMIZER_DENIED_USER" <<'PY'
import base64, hashlib, json, os, sys
path, users_csv, denied = sys.argv[1], sys.argv[2], sys.argv[3]
users = [item for item in users_csv.split(',') if item]
user_id = lambda name: hashlib.sha256(('local:' + name).encode()).hexdigest()[:16]
data = {
    'users': users,
    'denied_user': denied,
    'allowed_user_ids': {name: user_id(name) for name in users},
    'denied_user_id': user_id(denied),
    'credential_key_v1': base64.b64encode(os.urandom(32)).decode(),
    'datasource_password_key': base64.b64encode(os.urandom(32)).decode(),
    'optimizer_cursor_key': base64.b64encode(os.urandom(32)).decode(),
    'agent_internal_caller_key': base64.urlsafe_b64encode(os.urandom(24)).decode().rstrip('='),
}
with open(path, 'w') as handle:
    json.dump(data, handle, indent=2)
os.chmod(path, 0o600)
PY
  echo "generated keys: $KEYS"
}

key() { python3 -c "import json;print(json.load(open('$KEYS'))['$1'])"; }

sql_service_jar() {
  local jar
  jar=$(ls "$REPO_ROOT"/service/target/zorth-web-sql-service-*.jar 2>/dev/null | head -1 || true)
  if [ -z "$jar" ]; then
    echo "building SQL service jar..." >&2
    (cd "$REPO_ROOT/service" && env JAVA_HOME="$(resolve_java)" mvn -q -DskipTests package)
    jar=$(ls "$REPO_ROOT"/service/target/zorth-web-sql-service-*.jar | head -1)
  fi
  echo "$jar"
}

agent_jar() {
  local jar
  jar=$(ls "$AGENT_REPO"/target/ai-agent-platform-*.jar 2>/dev/null | head -1 || true)
  if [ -z "$jar" ]; then
    echo "building Agent Platform jar..." >&2
    (cd "$AGENT_REPO" && env JAVA_HOME="$(resolve_java)" mvn -q -DskipTests package)
    jar=$(ls "$AGENT_REPO"/target/ai-agent-platform-*.jar | head -1)
  fi
  echo "$jar"
}

start() { # start <name> <port> <command...>
  local name=$1 port=$2; shift 2
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    die "port $port ($name) is already in use; stop it first"
  fi
  mkdir -p "$LOGS"
  nohup "$@" >"$LOGS/$name.log" 2>&1 &
  echo $! > "$RUNTIME/$name.pid"
  echo "started $name (pid $(cat "$RUNTIME/$name.pid"), log $LOGS/$name.log)"
}

# Any HTTP answer (including 404) means the listener is up.
wait_port() {
  local port=$1 deadline=$((SECONDS + 120))
  until curl -s -o /dev/null "http://127.0.0.1:$port/"; do
    if [ $SECONDS -ge $deadline ]; then
      echo "warning: port $port did not answer within 120s" >&2
      return 0
    fi
    sleep 2
  done
}

up() {
  need MYSQL_HOST; need MYSQL_PORT; need MYSQL_USER; need MYSQL_PASSWORD; need LLM_API_KEY
  [ -d "$AGENT_REPO" ] || die "Agent Platform checkout not found: $AGENT_REPO (set AGENT_REPO)"
  mkdir -p "$RUNTIME" "$LOGS"
  chmod 700 "$RUNTIME"
  generate_keys
  local java; java=$(resolve_java)
  local sql_jar agent_jar
  sql_jar=$(sql_service_jar)
  agent_jar=$(agent_jar)

  local allowed_ids
  allowed_ids=$(python3 -c "import json;d=json.load(open('$KEYS'));print(','.join(d['allowed_user_ids'].values()))")

  start auth-service "$AUTH_PORT" env AUTH_SERVICE_PORT="$AUTH_PORT" \
    AUTH_SERVICE_INTERNAL_KEY=local-sql-editor-key AUTH_SERVICE_PRODUCT_ID=local-product \
    AUTH_SERVICE_PRODUCT_NAME=本地开发产品 node "$REPO_ROOT/auth-service/server.js"

  start sql-service "$API_PORT" env \
    SERVER_PORT="$API_PORT" \
    SQL_EDITOR_METADATA_URL="jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_METADATA_DATABASE}?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true" \
    SQL_EDITOR_METADATA_USERNAME="$MYSQL_USER" \
    SQL_EDITOR_METADATA_PASSWORD="$MYSQL_PASSWORD" \
    SQL_EDITOR_AUTH_CONTEXT_URL="http://127.0.0.1:$AUTH_PORT/internal/api/v1/auth/context" \
    SQL_EDITOR_AUTH_INTERNAL_SERVICE_KEY=local-sql-editor-key \
    SQL_EDITOR_CREDENTIAL_CURRENT_KEY="$(key datasource_password_key)" \
    SQL_EDITOR_CURSOR_SIGNING_KEY="$(key optimizer_cursor_key)" \
    SQL_EDITOR_NETWORK_ALLOWED_CIDRS="127.0.0.0/8,$(python3 -c "import socket,sys;print(socket.gethostbyname(sys.argv[1])+'/32')" "$MYSQL_HOST")" \
    SQL_EDITOR_NETWORK_DENIED_CIDRS='169.254.0.0/16,::1/128,fe80::/10' \
    SQL_EDITOR_OPTIMIZATION_ENABLED=true \
    SQL_EDITOR_OPTIMIZATION_INTEGRATION_READY=true \
    SQL_EDITOR_OPTIMIZATION_ALLOWED_USERS="$allowed_ids" \
    SQL_EDITOR_OPTIMIZATION_ALLOWED_PRODUCTS=local-product \
    SQL_EDITOR_OPTIMIZATION_AGENT_BASE_URL="http://127.0.0.1:$AGENT_PORT" \
    SQL_EDITOR_OPTIMIZATION_SCHEDULER_ENABLED="$OPTIMIZATION_SCHEDULER_ENABLED" \
    SQL_EDITOR_OPTIMIZATION_DEVELOPMENT_USER_HEADER=true \
    SQL_EDITOR_OPTIMIZATION_CREDENTIAL_KEY_V1="$(key credential_key_v1)" \
    SQL_EDITOR_OPTIMIZATION_CURSOR_KEY="$(key optimizer_cursor_key)" \
    SQL_EDITOR_AGENT_INTERNAL_CALLER_KEY_REQUIRED="$INTERNAL_CALLER_KEY_REQUIRED" \
    SQL_EDITOR_AGENT_INTERNAL_CALLER_KEY="$(key agent_internal_caller_key)" \
    "$java/bin/java" -jar "$sql_jar" --spring.profiles.active=local-optimization

  start agent-platform "$AGENT_PORT" env \
    LLM_API_KEY="$LLM_API_KEY" \
    SERVER_PORT="$AGENT_PORT" \
    DATASOURCE_SERVICE_ENABLED=true \
    DATASOURCE_SERVICE_BASE_URL="http://127.0.0.1:$API_PORT" \
    DATASOURCE_SERVICE_READ_TIMEOUT=35s \
    DATASOURCE_QUERY_ENABLED=false \
    DATASOURCE_INTERNAL_CALLER_KEY="$(key agent_internal_caller_key)" \
    "$java/bin/java" -jar "$agent_jar" --spring.profiles.active=dev

  start web "$WEB_PORT" env \
    VITE_SQL_API_BASE=/sql-api VITE_AUTH_API_BASE=/auth-api VITE_AI_API_BASE=/ai-api \
    VITE_DEV_SQL_PROXY_TARGET="http://127.0.0.1:$API_PORT" \
    VITE_DEV_AUTH_PROXY_TARGET="http://127.0.0.1:$AUTH_PORT" \
    VITE_DEV_AI_PROXY_TARGET="http://127.0.0.1:$AGENT_PORT" \
    VITE_LEGACY_PORTAL_URL='http://localhost:8080/account/bind' \
    VITE_AUTH_BRIDGE_ALLOWED_ORIGINS='http://localhost:8080' \
    VITE_AUTH_PRODUCT_TYPE=chinaBank \
    VITE_ENABLE_API_MOCK=false \
    bash -c "cd '$REPO_ROOT/web' && exec pnpm dev --host 127.0.0.1 --port $WEB_PORT"

  python3 - "$STATE" "$RUNTIME" "$KEYS" "$API_PORT" "$AUTH_PORT" "$AGENT_PORT" "$WEB_PORT" \
    "$MYSQL_HOST" "$MYSQL_PORT" "$MYSQL_USER" "$MYSQL_DEMO_DATABASE" "$OPTIMIZER_PASSWORD" "$OPTIMIZATION_SCHEDULER_ENABLED" <<'PY'
import json, sys
state, runtime, keys, api, auth, agent, web, host, port, user, demo, password, scheduler = sys.argv[1:14]
data = {
    'runtime': runtime, 'keys': keys,
    'ports': {'api': int(api), 'auth': int(auth), 'agent': int(agent), 'web': int(web)},
    'mysql': {'host': host, 'port': int(port), 'user': user, 'demoDatabase': demo},
    'loginPassword': password, 'schedulerEnabled': scheduler == 'true',
}
with open(state, 'w') as handle:
    json.dump(data, handle, indent=2)
PY
  wait_port "$API_PORT"; wait_port "$AGENT_PORT"; wait_port "$WEB_PORT"
  echo "stack is up; runtime state: $STATE (runtime.json holds the same values)"
  cp "$STATE" "$RUNTIME/runtime.json"
}

down() {
  for name in web agent-platform sql-service auth-service; do
    local pidfile="$RUNTIME/$name.pid"
    if [ -f "$pidfile" ]; then
      local pid; pid=$(cat "$pidfile")
      if kill -0 "$pid" 2>/dev/null; then
        kill -TERM "$pid" 2>/dev/null || true
        echo "stopped $name (pid $pid)"
      fi
      rm -f "$pidfile"
    fi
  done
}

status() {
  for name in auth-service sql-service agent-platform web; do
    local pidfile="$RUNTIME/$name.pid"
    if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
      echo "$name: running (pid $(cat "$pidfile"))"
    else
      echo "$name: stopped"
    fi
  done
}

case "${1:-}" in
  up) up ;;
  down) down ;;
  restart) down; sleep 3; up ;;
  status) status ;;
  *) die "usage: stack.sh <up|down|restart|status>" ;;
esac
