# Trackr

Trackr is a full-stack internship and job application tracker that brings your opportunities, application dates, interviews, and progress into one workspace. Its React frontend connects to a Spring Boot REST API, with accounts and application data stored in PostgreSQL.

The frontend and backend share this repository. You can run them separately for local development or deploy them together with PostgreSQL using Docker Compose on an Oracle VM. Authentication and data storage are handled by Spring Boot and PostgreSQL, not Firebase.

## Features

- Register and sign in with an email/password account or Google.
- Recover a forgotten password through an expiring email link.
- Update your display name, sign-in email, and password from account settings.
- Keep each user's applications private to their account.
- Create, view, edit, and delete applications.
- Track applications through Saved, Applied, Interview, Offer, Rejected, and Withdrawn statuses.
- Store company names, job titles, locations, job posting URLs, application dates, and interview dates.
- Search by company, role, or location and filter by status in the dashboard.
- View totals for applications, active applications, interviews, and offers.
- Browse a monthly calendar of application and interview dates with a selected-day details panel.
- Hover over or focus calendar jobs to see the company, position, location, dates, and posting link.
- Build a daily application streak after applying on at least two consecutive days.
- Navigate between Dashboard, Applications, and Calendar using a screen-height desktop sidebar or mobile navigation.
- Access Settings and Sign out from the account menu, with a simple initials-based profile badge.
- Use a responsive interface on desktop and smaller screens.

### Calendar and streaks

Click anywhere inside a calendar day, or click one of its jobs, to load that day's events in the details panel. Select an event in that panel to edit the application. Month navigation and a Today button make it easy to return to the current date. Application and interview events come from `dateApplied` and `interviewDate`, not just the current status: advancing an application to Interview or Offer does not remove its original application-date event. Editing either date updates the calendar immediately after saving.

The streak badge counts distinct local calendar dates with a recorded application date and a status other than Saved. Multiple applications on one day count once; future dates do not count. The badge starts at two consecutive days and stays active while the current day is still available to apply. Missing a full day breaks the streak. It is calculated from your existing application records, not stored as a separate counter.

### Visual identity

Trackr uses a Ruby Red wordmark, a rising-checkmark logo, and the following core palette:

| Color | Hex |
| --- | --- |
| Ruby Red | `#A31621` |
| Snow | `#FCF7F8` |
| Pale Slate | `#CED3DC` |
| Air Force Blue | `#4E8098` |
| Sky Blue | `#90C2E7` |

Profiles use a text badge derived from the display name. Animal-avatar selection and custom photo uploads are not included.

## Tech stack

- **Frontend:** React, Vite, CSS, and Lucide icons.
- **Backend:** Java 25, Spring Boot, Spring Security, Spring MVC, Spring Data JPA, Jakarta Validation, and Actuator health checks.
- **Authentication and mail:** Server-side sessions, BCrypt password hashing, optional Google OpenID Connect, and SMTP password-recovery emails.
- **Database:** PostgreSQL; isolated H2 databases for backend integration tests.
- **Build tools:** Maven Wrapper and npm.
- **Deployment:** Docker Compose, multi-stage Docker builds, and Caddy for static frontend hosting, reverse proxying, and automatic HTTPS.

## Project structure

```text
internship-tracker/
├── frontend/
│   ├── src/               # Dashboard, calendar, authentication, settings, styles, tests
│   ├── public/            # Source logo, favicon, and home-screen icon assets
│   ├── Dockerfile         # Build React, then serve the output with Caddy
│   └── Caddyfile          # HTTPS, static hosting, and backend routing
├── src/main/java/         # Spring Boot controllers, services, entities, and repositories
├── src/main/resources/    # Base, production, mail, and Google configuration
├── src/test/              # Backend tests and isolated test configuration
├── deploy/
│   ├── README.md          # Full Oracle VM deployment and recovery guide
│   └── .env.example       # Deployment settings template; no credentials
├── scripts/               # Deployment setup, deployment, backup, and secret checks
├── .githooks/             # Optional pre-commit secret check
├── .dockerignore          # Restricts the backend Docker build context
├── Dockerfile             # Build and run the Spring Boot JAR
├── compose.yaml           # Caddy/frontend, backend, and private PostgreSQL services
├── pom.xml                # Maven dependencies and build configuration
└── mvnw                   # Maven Wrapper
```

Generated folders and private configuration are intentionally omitted from this tree. They should not be committed or copied as source onto the VM.

### Source assets versus generated files

| Path | Purpose | Handling |
| --- | --- | --- |
| `frontend/public/` | Original images copied into the frontend build | Keep in Git; edit assets here |
| `frontend/src/` | React source, styles, and frontend tests | Keep in Git |
| `frontend/dist/` | Compiled website and copies of public images | Git-ignored; recreated by `npm run build` |
| `frontend/node_modules/` | Installed development/build dependencies | Git-ignored; recreated by `npm ci` |
| `target/` | Compiled backend, test reports, and packaged JAR | Git-ignored; recreated by Maven |
| `.secrets/` and `.env` | Private local/runtime configuration | Never commit or include in images |
| `backups/` | Database dumps created by the backup script | Git-ignored; protect and copy off the VM |

The image copies in `dist/` are expected build output, not extra source files. `favicon.ico` and `trackr-icon-32.png` provide browser-tab icon formats; `trackr-apple-touch-icon.png` is the home-screen icon for Apple devices; `trackr-logo.png` is used inside the application. All four source assets are currently referenced. Do not edit their generated `dist/` copies.

## Docker hosting on an Oracle VM

The production Compose setup builds the frontend and backend, runs PostgreSQL privately, and serves the app with automatic HTTPS through Caddy. It supports native ARM64 and AMD64 builds. Only the web service publishes ports 80/443; the database password, SMTP key, and optional Google client secret are mounted from ignored files at runtime. The final frontend container serves the compiled website, not the Vite development server or `node_modules`.

Follow [the Oracle VM deployment guide](deploy/README.md) for the full commands and troubleshooting. After obtaining access to the VM:

1. Connect over SSH. Keep the SSH private key on your own computer, outside the repository.
2. Install Docker Engine, Buildx, and the Compose plugin. Configure an authorized Docker operator account for the scripts.
3. Allow public TCP 80/443 through Oracle's NSG/security list and the host firewall. Restrict SSH 22 to your own IP; do not publish PostgreSQL or the backend.
4. Point a domain's DNS A record to the VM's public IPv4 address. The supplied configuration expects a public domain and HTTPS, not an IP-only deployment.
5. Clone or transfer the project source, excluding local secrets, dependencies, build output, and backups.
6. Run the setup script, fill in the deployment settings, then build and start the services:

```bash
bash scripts/setup-deployment.sh
nano .env
bash scripts/deploy.sh
```

The setup script creates an ignored `.env`, generates a database password once, and privately prompts for the SMTP key and optional Google secret. Rerunning it preserves existing nonempty credentials. The tracked `deploy/.env.example` is a deployment template, not a file Spring Boot automatically loads.

Set `APP_DOMAIN` to a bare hostname, `MAIL_USERNAME` to your Brevo SMTP login, and `MAIL_FROM` to your verified sender. Keep `SPRING_PROFILES_ACTIVE=production,mail`; add `,google` and `GOOGLE_CLIENT_ID` only when configured. The setup script stores real secrets in `.secrets/docker/`, not in `.env` or frontend code. For Google, register your public origin and the callback `https://YOUR_DOMAIN/login/oauth2/code/google` in Google Cloud.

The production profile enables secure session cookies, proxy-header handling, graceful shutdown, and a health-only Actuator exposure. Caddy routes `/api`, `/oauth2`, and `/login/oauth2` to the backend and blocks public Actuator access. Containers include health probes, restart policies, and bounded log rotation. Health checks do not prove that public DNS, HTTPS, SMTP delivery, or Google sign-in work; test those after startup.

### Operations, persistence, and updates

Run these from the project directory on the VM:

```bash
docker compose ps
docker compose logs --tail=100 backend frontend
bash scripts/backup-db.sh
```

Named Docker volumes preserve PostgreSQL data and Caddy certificates across rebuilds. A fresh VM database is empty: local users, example applications, and demo-account data are not transferred automatically. See the deployment guide before restoring an existing compatible PostgreSQL dump into a new, empty database.

Copy backups to protected storage outside the VM, test restoration, and protect runtime secrets separately. Back up before updating the source and rerunning `bash scripts/deploy.sh`. Never run `docker compose down --volumes` or prune project volumes unless you intend to erase their data. Changing a database secret file alone does not rotate the password in an already-initialized PostgreSQL database.

This repository provides deployment files; it does not provision the VM, register a domain, configure firewall rules, or deploy itself. Local development remains available below.

## Getting started

### Prerequisites

- JDK 25 or newer.
- A Node.js version supported by the installed Vite release, with npm.
- A running PostgreSQL server.

### 1. Configure PostgreSQL

Create a database named `internship_tracker` using a PostgreSQL role that can create tables in it:

```bash
createdb -U your_postgres_username internship_tracker
```

The backend defaults to `jdbc:postgresql://localhost:5432/internship_tracker` with username `devinfischer`. Override the username and provide your database password in the terminal where you will start the backend:

```bash
export SPRING_DATASOURCE_USERNAME=your_postgres_username
export DB_PASSWORD='your_postgres_password'
```

If your database runs elsewhere, also set `SPRING_DATASOURCE_URL`. The configuration is in `src/main/resources/application.properties`; Hibernate creates or updates the application tables on startup.

### 2. Start the backend

From the repository root:

```bash
./mvnw spring-boot:run
```

The API runs at [http://localhost:8080/api/applications](http://localhost:8080/api/applications). On Windows, use `mvnw.cmd` instead of `./mvnw`.

### 3. Start the frontend

Open a second terminal and run these commands from the repository root:

```bash
cd frontend
npm ci
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). Vite forwards `/api` requests to the backend on port `8080`, as configured in `frontend/vite.config.js`.
It also forwards `/oauth2` and `/login/oauth2` for Google sign-in.

Both servers and PostgreSQL must be running to use the full application. `npm run dev` starts only the frontend.

### Configuration profiles and secrets

| Configuration | When it is used |
| --- | --- |
| `application.properties` | Base backend defaults, including local PostgreSQL and frontend URLs |
| `.secrets/application.properties` | Optional ignored local overrides, imported when starting from the repository root |
| `application-mail.properties` | SMTP settings when the `mail` profile is active |
| `application-google.properties` | Google OAuth settings when the `google` profile is active |
| `application-production.properties` | Docker/public HTTPS settings when the `production` profile is active |
| `.env` | Compose interpolation for non-secret deployment settings; not automatically a local Spring settings file |
| `.secrets/docker/` | Runtime secret files mounted into the Docker services |

Activate multiple profiles with a comma-separated `SPRING_PROFILES_ACTIVE`. Docker imports `/run/secrets/` as a Spring configuration tree. Do not put runtime credentials into tracked properties files or either Docker build context.

## Accounts and authentication

Create an account from the login screen using your name, email, and a password of at least 12 characters (maximum 72 UTF-8 bytes). Passwords are stored as salted BCrypt hashes, never plaintext or reversible encryption. Login uses a server-side session with an HttpOnly, SameSite=Lax cookie and a 30-minute inactivity timeout. Mutating requests, including login and logout, require CSRF tokens. See the [Spring Security password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html) and [CSRF documentation](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html).

Every application endpoint checks the authenticated owner. Existing application records created before accounts were added retain a null `owner_id` and are hidden from all users. They are preserved in PostgreSQL; assign them deliberately to the appropriate user as a separate data migration rather than exposing them to the first person who registers.

### Account settings

Open the arrow next to your name and choose **Settings**, above **Sign out**. The pop-up includes Profile and Password & security sections. Name updates appear immediately. Email and password changes require your current password, clear outstanding reset links, and sign out all existing sessions; sign in again with the updated credentials. Email changes preserve your applications and send a notice to both the previous and new address when SMTP is configured. The new email is not verified by a confirmation link, so check it carefully before saving.

Google users can edit their Trackr display name, but manage their email and password through Google. The shared demo account's email and password cannot be changed through Settings or the API.

### Demo account

The reserved local test account is `demo@trackr.test`. On the configured local installation, its generated password is kept in the Git-ignored `.secrets/demo-account.txt` file. Log in normally using those credentials. It has ordinary user permissions; provisioning does not automatically seed example applications, and existing applications are preserved.

The backend disables reset-link requests, reset-token redemption, and Settings credential changes for this account, including previously issued tokens. Forgot-password requests keep the same generic response. Regular accounts remain resettable, and registration cannot change the reset policy or claim the reserved demo email.

Demo provisioning is disabled in the committed configuration. To enable it on another development installation, put `app.demo.enabled=true` and a private `app.demo.password` (12 characters minimum, 72 UTF-8 bytes maximum) in its ignored `.secrets/application.properties`. The demo user is created once; subsequent starts preserve its password and data. Setting `app.demo.enabled=false` stops provisioning but does not delete an existing demo account. Never commit a demo password or reuse an owner/email-service password for it.

### Password recovery setup

Choose **Forgot password?** on the sign-in screen, enter your email, and follow the emailed link to choose and confirm a new password. Reset links expire after 30 minutes and can only be used once. The database stores only a SHA-256 hash of each random token. A new request replaces the previous link, with a one-minute cooldown between emails. Requests return the same message for existing, unknown, and Google-only accounts; Google-only users should continue signing in with Google.

After a reset, the app invalidates existing password sessions, sends a confirmation email, and returns to sign-in. It does not automatically log the user in. The reset URL uses a fragment so the token is not sent in page requests or referrer headers. These choices follow the [OWASP forgot-password guidance](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).

SMTP must be configured to deliver recovery emails. Choose ignored local configuration or environment variables:

For local Brevo development, you can use the existing ignored `.secrets/application.properties`, imported when Spring starts from the repository root. Keep the SMTP login, SMTP key, and verified sender there; the login and sender are different settings. Otherwise use the environment variables below with the `mail` profile. Docker production instead uses mounted secret files as described in the deployment guide.

Alternatively, use environment variables and the `mail` profile:

```bash
export MAIL_HOST='smtp.your-provider.com'
export MAIL_PORT=587
export MAIL_USERNAME='your-smtp-username'
export MAIL_PASSWORD='your-smtp-password'
export MAIL_FROM='Trackr <your-verified-sender@example.com>'
export SPRING_PROFILES_ACTIVE=mail
./mvnw spring-boot:run
```

If Google sign-in is also enabled, use `SPRING_PROFILES_ACTIVE=google,mail`. Set `FRONTEND_URL` to your public HTTPS URL when deployed; it controls the trusted origin in reset links. The application does not read SMTP secrets from frontend code or require them to be committed.

The mail profile requires SMTP authentication and STARTTLS by default and sets five-second network timeouts. For an existing local SMTP test server, override `MAIL_HOST`, `MAIL_PORT`, `MAIL_SMTP_AUTH=false`, and `MAIL_STARTTLS=false` as needed. Never disable TLS for a public SMTP service. See [Spring Boot's mail configuration](https://docs.spring.io/spring-boot/reference/io/email.html).

Without SMTP and a sender address, recovery requests return HTTP 503 with an explicit configuration message. Mail is dispatched asynchronously after the reset token commits; if SMTP delivery fails, the server logs a warning without the token or recipient. Production deployments should add provider monitoring and request-level rate limiting; the current cooldown is per account. The in-memory session registry assumes a single backend instance.

### Google sign-in setup

Email/password login works without Google configuration. To enable Google sign-in:

1. In [Google Cloud Console](https://console.cloud.google.com/), select or create a project and configure its OAuth consent screen. Add test users if the app's audience is restricted to testing.
2. Create an OAuth client with application type **Web application**. Add `http://localhost:5173/login/oauth2/code/google` as an authorized redirect URI. It must match exactly; see [Google's OpenID Connect guide](https://developers.google.com/identity/openid-connect/openid-connect).
3. In the backend terminal, set the credentials and activate the Google profile:

   ```bash
   export GOOGLE_CLIENT_ID='your-client-id'
   export GOOGLE_CLIENT_SECRET='your-client-secret'
   export SPRING_PROFILES_ACTIVE=google
   ./mvnw spring-boot:run
   ```

Restart the backend after changing these variables. Open the frontend at `http://localhost:5173`; the **Continue with Google** button appears when Google is configured. Vite proxies both the authorization and callback paths to Spring Boot, keeping the session on the same browser origin. Credentials stay in the backend environment; do not put the client secret in frontend files or commit it.

Google accounts use the validated OpenID Connect subject identifier and require a verified Google email. If an email already belongs to a password account, Google sign-in will not automatically link it; sign in using the original method. Account linking, email verification for password accounts, and login throttling are not implemented.

For deployment, serve the frontend and backend under one HTTPS origin and route `/api`, `/oauth2`, and `/login/oauth2` to Spring Boot. The supplied Compose configuration derives `FRONTEND_URL` from `APP_DOMAIN`, the Google profile derives the callback from that frontend URL, and the production profile enforces secure cookies. For a different deployment, set the equivalent values explicitly; `GOOGLE_REDIRECT_URI` can override the callback. Sessions are stored in memory and end when the backend restarts.

## REST API

Application endpoints require a logged-in session and return only that user's records. Unauthenticated requests return HTTP 401; requests for records belonging to another user return HTTP 404.

| Method | Authentication endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/auth/config` | Check whether Google sign-in is enabled |
| GET | `/api/auth/csrf` | Get a CSRF token and its header name |
| POST | `/api/auth/register` | Create an email/password account (JSON) |
| POST | `/api/auth/login` | Sign in with form-encoded `username` (email) and `password` |
| POST | `/api/auth/forgot-password` | Request a reset email with JSON `{ "email": "..." }` |
| POST | `/api/auth/reset-password` | Use a valid link with JSON `{ "token": "...", "password": "..." }` |
| GET | `/api/auth/me` | Get the current user's public profile |
| PATCH | `/api/auth/me` | Update name/email with JSON `{ "name": "...", "email": "...", "currentPassword": "..." }`; password required only when email changes |
| PUT | `/api/auth/password` | Change password with JSON `{ "currentPassword": "...", "newPassword": "..." }` |
| POST | `/api/auth/logout` | Invalidate the session |
| GET | `/oauth2/authorization/google` | Start Google sign-in when configured |

Include the CSRF token header on POST, PATCH, PUT, and DELETE requests. Obtain a fresh token after login or logout because authentication changes invalidate the previous token. The frontend handles this automatically.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/applications` | List applications |
| GET | `/api/applications/{id}` | Get one application |
| POST | `/api/applications` | Create an application |
| PUT | `/api/applications/{id}` | Update an application |
| DELETE | `/api/applications/{id}` | Delete an application |

The list endpoint accepts optional `status`, `company`, and `search` parameters. `company` matches company names and `search` matches job titles, both case-insensitively. When multiple parameters are supplied, the backend uses the first applicable filter in this order: status, company, search. The frontend applies its combined search and status filters locally.

Example request body for creating an application:

```json
{
  "companyName": "Example Company",
  "jobTitle": "Software Engineering Intern",
  "status": "APPLIED",
  "location": "Remote",
  "jobUrl": "https://example.com/careers/intern",
  "dateApplied": "2026-10-20",
  "interviewDate": null
}
```

Company name and job title are required. Dates use `YYYY-MM-DD`, and status values use the uppercase names listed above. New applications default to `SAVED` when status is omitted.

## Build and test

### Keeping credentials out of Git

The `.secrets/` directory, `.env` files, private-key files, and local Spring configuration are ignored by Git. The example configuration contains no credentials. Keep real credentials outside `src/main/resources` so they are not bundled into the backend JAR, and never expose them through Vite's frontend environment variables.

Run `node scripts/check-secrets.mjs` to inspect publishable working-tree files or `node scripts/check-secrets.mjs --staged` to inspect the actual Git index. Findings report only filenames, line numbers, and credential types. The check detects common provider keys and hardcoded credential settings; it is a best-effort safeguard, not proof that no possible secret exists.

Enable the pre-commit check in each clone:

```bash
git config core.hooksPath .githooks
```

Ignoring a file does not remove it from existing commits or the staging area. If a credential was shared in chat, committed, or published, revoke it with its provider and generate a new one. Rotation is required even after removing it from files. Review staged changes before publishing and enable GitHub secret scanning/push protection where available. Local checks cannot verify an unrelated public repository.

### Verification commands

Run backend tests from the repository root. Tests use an isolated in-memory H2 database and do not require PostgreSQL or Google credentials:

```bash
./mvnw test
```

Run backend verification and build the executable JAR at `target/trackr.jar`:

```bash
./mvnw verify
```

Run frontend tests and build from `frontend/`:

```bash
npm test
npm run build
```

The frontend build is written to `frontend/dist/`. It is built separately from the backend and is not included in the Spring Boot JAR. Docker builds both artifacts and configures production routing through Caddy. The local Vite proxy is only used during development.

Frontend tests cover calendar date generation, application/interview events, and streak calculations. Backend tests cover authentication, ownership, recovery, settings, demo-account protections, Google configuration, and production configuration/secret resolution. The deployment script validates Compose and Caddy configuration and waits for healthy services; a real VM deployment still needs end-to-end testing.

## Current limitations and remaining launch work

- Password-account email verification and verification of a newly changed email are not implemented. Google sign-in requires Google's verified-email claim.
- General authentication/request rate limiting is not implemented. The one-minute password-reset email cooldown is per account, not a complete abuse-prevention system.
- Sessions and session tracking are in memory. Backend restarts sign users out; the deployment is designed for one backend instance.
- Schema changes use Hibernate `ddl-auto=update`, not versioned database migrations. Back up before updates and introduce migrations before substantial production schema changes.
- Local database contents are not automatically copied to the VM. Backup scheduling, off-VM storage, restore testing, and SMTP-delivery monitoring require operator setup.
- Animal avatars, photo uploads, and linking an existing password account to Google are not included.

Docker hosting files do not make these features complete. Review these limitations before opening registration widely.
