#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v docker >/dev/null || { echo "Install Docker Engine and the Compose plugin first." >&2; exit 1; }
docker compose version >/dev/null
[[ -f .env ]] || { echo "Run bash scripts/setup-deployment.sh first." >&2; exit 1; }
for secret in db_password mail_password; do
  [[ -s ".secrets/docker/$secret" ]] || { echo "Missing $secret. Run the setup script to configure it." >&2; exit 1; }
done

profiles="${SPRING_PROFILES_ACTIVE:-$(sed -n 's/^SPRING_PROFILES_ACTIVE=//p' .env)}"
profiles="${profiles:-production,mail}"
for required in production mail; do
  case ",$profiles," in
    *",$required,"*) ;;
    *) echo "Keep production and mail in SPRING_PROFILES_ACTIVE." >&2; exit 1 ;;
  esac
done
case ",$profiles," in
  *",google,"*)
    [[ -s .secrets/docker/google_client_secret ]] || { echo "Configure google_client_secret before enabling Google." >&2; exit 1; }
    ;;
esac

docker compose config --quiet
docker compose build --pull
docker compose run --rm --no-deps frontend caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
docker compose up --detach --wait --wait-timeout 180
docker compose ps
