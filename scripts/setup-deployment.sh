#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v openssl >/dev/null || { echo "Install openssl first." >&2; exit 1; }

umask 077
if [[ -L .env || -L .secrets || -L .secrets/docker || -L .secrets/docker/db_password ]]; then
  echo "Refusing to write deployment configuration through symlinks." >&2
  exit 1
fi
mkdir -p .secrets/docker
chmod 700 .secrets .secrets/docker
if [[ ! -e .env ]]; then
  cp deploy/.env.example .env
fi
chmod 600 .env
if [[ ! -e .secrets/docker/db_password ]]; then
  openssl rand -hex 32 > .secrets/docker/db_password
elif [[ ! -s .secrets/docker/db_password ]]; then
  echo "The existing database password file is empty. Resolve it without changing a running database password." >&2
  exit 1
fi

for secret in mail_password google_client_secret; do
  path=".secrets/docker/$secret"
  if [[ -L "$path" ]]; then
    echo "Refusing to write through a secret-file symlink." >&2
    exit 1
  fi
  if [[ ! -e "$path" ]]; then
    printf '' > "$path"
  fi
  if [[ ! -s "$path" && -t 0 ]]; then
    value=''
    read -r -s -p "Enter $secret (Enter to configure later): " value || true
    printf '\n'
    if [[ -n "$value" ]]; then
      printf '%s' "$value" > "$path"
    fi
    unset value
  fi
done

# Compose file secrets are bind-mounted: the non-root app needs read access.
# The parent directories stay owner-only, and container mounts are read-only.
chmod 644 .secrets/docker/db_password .secrets/docker/mail_password .secrets/docker/google_client_secret
echo "Deployment files are ready. Edit .env with your domain, SMTP login, and verified sender."
echo "Run this script again to fill empty mail/Google secrets. Existing nonempty secrets are preserved."
echo "Then run: bash scripts/deploy.sh"
