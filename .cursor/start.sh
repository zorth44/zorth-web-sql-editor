#!/usr/bin/env bash
# Per-boot reconciliation: ensure MySQL is running before the service
# terminals start. Idempotent and safe to run on every boot.
set -euo pipefail

echo "==> Ensuring MySQL is running"
sudo service mysql start
for _ in $(seq 1 30); do
  if sudo mysqladmin ping >/dev/null 2>&1; then
    echo "==> MySQL is ready"
    exit 0
  fi
  sleep 1
done

echo "MySQL did not become ready in time" >&2
exit 1
