# Oracle VM deployment

This is a single-VM Docker Compose deployment. Caddy serves the production React build and routes the API and Google OAuth paths to Spring Boot. PostgreSQL is on a private Docker network. Only TCP 80 and 443 are published. Java and Caddy run as non-root users; credentials are mounted as file secrets, not built into images or passed as container environment values.

The official Node 24, Temurin 25, Caddy 2, and PostgreSQL 17 images support AMD64 and ARM64. Build natively on the VM, including an Oracle Ampere VM; no platform override is needed. Allow roughly 4 GB RAM for comfortable native builds of both applications. Image tags follow supported major versions; pin reviewed digests if you need strictly reproducible releases.

## 1. Prepare the VM

1. Create an Ubuntu VM with a public IP, public subnet, and internet gateway. Keep its SSH private key on your own computer, outside this repository.
2. Connect using the username in the instance details, typically `ubuntu`:

   ```bash
   ssh -i /path/to/private-key ubuntu@VM_PUBLIC_IP
   ```

3. Install Docker Engine with Buildx and the Compose plugin using [Docker's Ubuntu instructions](https://docs.docker.com/engine/install/ubuntu/). Verify `docker version` and `docker compose version`. Use `sudo` or a properly configured Docker operator account; membership in the Docker group grants root-equivalent access.
4. In the Oracle NSG/security list, allow TCP 80 and 443 from the internet. Restrict TCP 22 to your own IP. Ensure the VM's host firewall also allows web traffic; do not flush Oracle's existing firewall rules. Do not open 5432, 8080, 8081, or 8443 publicly. Docker's published ports can bypass normal UFW rules, so keep the Oracle network rules restrictive. See [Oracle's Ubuntu networking guide](https://docs.oracle.com/en-us/iaas/Content/developer/apache-on-ubuntu/01oci-ubuntu-apache-summary.htm).
5. Point a domain's DNS A record to the VM's public IPv4 address. Add an AAAA record only if IPv6 is configured correctly. Start with DNS-only mode if using a CDN/proxy. Caddy needs working public DNS and inbound 80/443 for automatic certificates; see [Caddy automatic HTTPS](https://caddyserver.com/docs/automatic-https).

## 2. Transfer source, not secrets

If you have pushed the project to GitHub, clone that repository on the VM. Do not copy `.secrets`, `.env`, `node_modules`, `target`, or `dist` from your laptop. If you have not published it, copy the source using SSH with those paths excluded. This project does not configure GitHub or create a VM automatically.

From the project folder on the VM:

```bash
bash scripts/setup-deployment.sh
nano .env
```

The setup script copies the deployment example into the ignored `.env`, generates a database password once, and prompts privately for the Brevo SMTP key and optional Google client secret. Empty values can be completed by running it again. Existing nonempty secret files and `.env` are not overwritten.

Set these `.env` values:

| Setting | Value |
| --- | --- |
| `APP_DOMAIN` | Bare hostname, e.g. `trackr.yourdomain.com`; no scheme, path, or port |
| `MAIL_USERNAME` | Brevo's SMTP login, not your sender address |
| `MAIL_FROM` | Your verified sender email |
| `SPRING_PROFILES_ACTIVE` | Keep `production,mail`; append `,google` only when configured |
| `GOOGLE_CLIENT_ID` | Your Google web OAuth client ID, when enabled |

Keep the database name/user defaults unless you need otherwise. Use the unquoted profile format shown in the example. Leave secret values out of `.env`: they belong in `.secrets/docker/db_password`, `mail_password`, and `google_client_secret`. These secret files are readable by the non-root containers, inside owner-only host directories; Compose mounts them read-only. Protect the VM and backups too—Compose file secrets are not encryption at rest. Never commit or send these files in chat.

For Google sign-in, register both of these in Google Cloud:

- Authorized JavaScript origin: `https://YOUR_DOMAIN`
- Authorized redirect URI: `https://YOUR_DOMAIN/login/oauth2/code/google`

The callback is derived from the configured public frontend URL. Do not use the old localhost callback for the hosted site. Google-only sign-in remains optional; email/password login works without the `google` profile.

## 3. Build and start

```bash
bash scripts/deploy.sh
```

The script validates Compose configuration, pulls/builds images, validates Caddy configuration, and waits for healthy services. It needs Docker Compose with `up --wait` support. Open `https://YOUR_DOMAIN` and test registration, login, application changes, logout, and password recovery. The default production configuration disables demo provisioning; it does not delete an existing demo account if you import one.

```bash
docker compose ps
docker compose logs --tail=100 backend frontend
docker compose exec backend curl --fail --silent http://127.0.0.1:8080/actuator/health
```

The Actuator health endpoint checks the application/database but is blocked by Caddy from public access. The frontend health probe checks its local HTTP listener; independently check your public HTTPS URL and certificate after DNS propagation. Health status alone does not guarantee mail delivery or Google OAuth configuration. Test a real recovery email yourself. Compose restart policies restart crashed containers, not merely unhealthy ones.

If HTTPS fails, check DNS, NSG rules, the host firewall, Caddy logs, and whether another service already occupies 80/443. Do not disable secure cookies to work around an unfinished domain/TLS setup. Caddy is the only trusted incoming proxy; forwarded headers are reset there. Do not expose the backend directly or put another proxy in front without reviewing its trust settings.

## 4. Data and backups

Docker volumes preserve PostgreSQL data and Caddy certificates across container rebuilds. A fresh deployment starts with an empty database; your laptop's accounts/applications are not uploaded automatically.

```bash
bash scripts/backup-db.sh
```

Copy the resulting private dump to protected storage on another machine and test restoring it. Protect `.env` and the Docker secret files separately: a database dump is not a credential/certificate backup. Schedule backups using your VM's preferred scheduler and monitor failures. Never use `docker compose down --volumes` or prune project volumes unless you deliberately intend to erase the database/certificates.

To import an existing compatible PostgreSQL dump, do so before the first backend startup, into a new empty target database:

```bash
docker compose up -d db
docker compose exec -T db sh -c 'exec pg_restore --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --no-owner --no-privileges' < /path/to/private-backup.dump
bash scripts/deploy.sh
```

Check PostgreSQL dump/restore version compatibility first. Do not run the import over existing application data. Do not mount a laptop PostgreSQL data directory into this container. Changing the password file does not change the password of an already-initialized database; rotate the PostgreSQL role and its secret together.

## 5. Updates and remaining launch work

Back up the database, update the source, then rerun `bash scripts/deploy.sh`. Keep `.env`, secret files, and volumes on the VM. API sessions are in memory: backend restarts require users to sign in again, and this setup is intended for one backend instance.

This deployment retains the existing Hibernate `ddl-auto=update` strategy. Back up before schema-changing updates; introduce versioned migrations before making substantial production schema changes. It does not implement the previously discussed email-verification or authentication-rate-limiting features. Those remain recommended before opening registration widely. It also does not create firewall rules, provision a VM, register a domain, transfer your local database, or upload local keys.
