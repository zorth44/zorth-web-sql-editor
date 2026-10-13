#!/usr/bin/env bash
# Idempotent bootstrap for the Zorth Web SQL Editor Cloud Agent environment.
# Installs system toolchains (JDK 17, Maven, MySQL), prepares the local
# databases, installs web dependencies, and builds the SQL service jar.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME_17="/usr/lib/jvm/java-17-openjdk-amd64"

echo "==> Installing system packages (maven, openjdk-17, mysql-server)"
if ! command -v mvn >/dev/null 2>&1 \
  || [ ! -d "$JAVA_HOME_17" ] \
  || ! command -v mysqld >/dev/null 2>&1; then
  sudo DEBIAN_FRONTEND=noninteractive apt-get update -qq
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
    maven openjdk-17-jdk-headless mysql-server
fi

export JAVA_HOME="$JAVA_HOME_17"
export PATH="$JAVA_HOME/bin:$PATH"

echo "==> Starting MySQL and applying local bootstrap schema"
sudo service mysql start
for _ in $(seq 1 30); do
  if sudo mysqladmin ping >/dev/null 2>&1; then break; fi
  sleep 1
done
sudo mysql < "$ROOT/.cursor/mysql-bootstrap.sql"

echo "==> Installing web dependencies (pnpm)"
corepack enable >/dev/null 2>&1 || true
cd "$ROOT/web"
pnpm install --frozen-lockfile

echo "==> Building SQL service jar (skipping tests)"
cd "$ROOT/service"
mvn -q -DskipTests clean package

echo "==> Install complete"
