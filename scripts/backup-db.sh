#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
mkdir -p backups
backup=$(mktemp "backups/trackr-$(date -u +%Y%m%dT%H%M%SZ).XXXXXX")
if docker compose exec -T db sh -c 'exec pg_dump --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --format=custom' > "$backup"; then
  echo "Database backup saved to $backup. Copy it to a separate, protected machine."
else
  rm -f -- "$backup"
  echo "Database backup failed; the incomplete file was removed." >&2
  exit 1
fi
