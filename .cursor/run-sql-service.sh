#!/usr/bin/env bash
# Spring Boot SQL service (API on 8080, management on 9081).
# Configuration values below are LOCAL DEVELOPMENT ONLY and mirror the
# published examples in docs/local-development.md and application.yml defaults.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/service"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$PATH"

export SQL_EDITOR_METADATA_URL="${SQL_EDITOR_METADATA_URL:-jdbc:mysql://127.0.0.1:3306/sqleditor?serverTimezone=UTC}"
export SQL_EDITOR_METADATA_USERNAME="${SQL_EDITOR_METADATA_USERNAME:-sqleditor_user}"
export SQL_EDITOR_METADATA_PASSWORD="${SQL_EDITOR_METADATA_PASSWORD:-sqleditor_password}"
export SQL_EDITOR_AUTH_CONTEXT_URL="${SQL_EDITOR_AUTH_CONTEXT_URL:-http://127.0.0.1:8090/internal/api/v1/auth/context}"
export SQL_EDITOR_AUTH_INTERNAL_SERVICE_KEY="${SQL_EDITOR_AUTH_INTERNAL_SERVICE_KEY:-local-sql-editor-key}"
export SQL_EDITOR_CREDENTIAL_CURRENT_KEY="${SQL_EDITOR_CREDENTIAL_CURRENT_KEY:-MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=}"
export SQL_EDITOR_CURSOR_SIGNING_KEY="${SQL_EDITOR_CURSOR_SIGNING_KEY:-YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=}"
# Allow loopback targets for local demo databases; keep link-local denied.
export SQL_EDITOR_NETWORK_ALLOWED_CIDRS="${SQL_EDITOR_NETWORK_ALLOWED_CIDRS:-127.0.0.0/8}"
export SQL_EDITOR_NETWORK_DENIED_CIDRS="${SQL_EDITOR_NETWORK_DENIED_CIDRS:-169.254.0.0/16,::1/128,fe80::/10}"

# Wait for MySQL to accept connections before starting.
for _ in $(seq 1 30); do
  if mysqladmin --host=127.0.0.1 --user="$SQL_EDITOR_METADATA_USERNAME" \
      --password="$SQL_EDITOR_METADATA_PASSWORD" ping >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

JAR="$(ls -t target/zorth-web-sql-service-*.jar 2>/dev/null | head -1 || true)"
if [ -z "$JAR" ]; then
  echo "SQL service jar not found; run ./.cursor/install.sh first" >&2
  exit 1
fi

exec java -jar "$JAR"
