# Shield VPN Architecture & Deployment Guide

This document describes the updated architecture for Shield VPN, which consists of a mobile client (currently Android/Jetpack Compose, designed to be platform-agnostic for future Flutter migration), a FastAPI backend, and a segregated backoffice/admin experience.

## A) High-Level Architecture

*   **Mobile App Architecture:**
    *   Currently implemented natively in Android (Jetpack Compose / Kotlin Coroutines).
    *   **State Management:** ViewModels (`VpnViewModel`) drive UI state, segregating pure UI from business logic.
    *   **Local Persistence:** Room Database acts as a local cache for offline rendering.
    *   **Network Layer:** Retrofit connects to the agnostic REST API backend.
*   **Backend Architecture:**
    *   Built with **FastAPI** (Python) for async REST endpoints.
    *   Provides two segregated domains: `/auth/` and `/servers/` for public/mobile clients, and `/admin/` for backoffice access.
    *   Uses **JWT (JSON Web Tokens)** for stateless, scalable session management.
*   **Database Architecture:**
    *   **SQLAlchemy** ORM connects to **PostgreSQL**.
    *   `DbUser` model manages both regular users and admins (differentiated by `is_admin` boolean).
    *   `DbServer` model houses VPN node configurations including WireGuard credentials.
*   **Backoffice Architecture:**
    *   Separated from the mobile client entirely. Admins authenticate exclusively via `/admin/auth/login`.
    *   Mobile app exposes NO admin-facing views or roles.
*   **WireGuard Integration Flow:**
    *   Mobile queries `/servers` to get VPN nodes.
    *   User selects a node and calls `/servers/wg-demo` (or similar actual endpoint) to fetch configuration (IP, Endpoint, Public Key).
    *   The Mobile `WireGuardVpnService` consumes these parameters to initiate a VpnService builder tunnel.
*   **Authentication Flow:**
    *   Users hit `/auth/signup` or `/auth/login` to receive a Bearer Token.
    *   Mobile stores token (in memory/cache) and injects it into subsequent API calls.
*   **Guest/Free-Tier Flow:**
    *   Users can bypass login ("Continue as Free User").
    *   Guest state sets `isAuthenticated = true` but `isUserPremium = false` locally without a token.
    *   Backend returns all servers; Mobile UI restricts connection to premium servers for non-premium/guest users.
*   **Premium Subscription Flow:**
    *   Upon successful In-App Purchase, Mobile hits `/subscriptions/verify`.
    *   Backend updates `is_premium = true` and `subscription_expiry`.
    *   Next login returns new privileges.

## B) Infrastructure Overview

*   **Docker Services:**
    *   `db`: PostgreSQL container holding users and servers.
    *   `web` (Backend): FastAPI application running via Uvicorn.
    *   `admin` (optional React/TS backoffice): Separate container for admin CMS.
*   **Ports:**
    *   Backend: 8000
    *   PostgreSQL: 5432
*   **Environment Variables:**
    *   `DATABASE_URL`: Connection string (e.g., `postgresql://user:pass@db:5432/shieldvpn`).
    *   `JWT_SECRET_KEY`: High-entropy key for signing tokens.
    *   `SEED_ADMIN_EMAIL` & `SEED_ADMIN_PASSWORD`: Used strictly during initial migration/boot to seed the first admin user securely.
*   **Internal Service Communication:**
    *   Mobile app points to `$API_BASE_URL/` (e.g., `http://10.0.2.2:8000` via Emulator or a public IP).
    *   FastAPI backend communicates directly to PostgreSQL on the internal Docker network.

## C) Deployment Instructions

1.  **Local Development Setup:**
    ```bash
    git clone <repo>
    cd shield-vpn
    ```

2.  **Database & Backend Startup (Docker Compose):**
    Ensure Docker is running.
    ```bash
    docker-compose up -d --build
    ```
    This spins up PostgreSQL and FastAPI.

3.  **Admin Dashboard:**
    Brought up by `docker compose up` alongside the rest, at
    <http://localhost:3000>. Sign in with `SEED_ADMIN_EMAIL` /
    `SEED_ADMIN_PASSWORD` from your `.env`.

    nginx in that container proxies `/api` to the backend, so the browser only
    ever talks to port 3000 and no backend hostname is baked into the bundle.

    To run it with hot reload instead:
    ```bash
    cd admin-dashboard && npm install && npm run dev
    ```
    The Vite dev server proxies `/api` to `http://localhost:8000`; override with
    `VITE_API_TARGET`.

4.  **Mobile App Connection Setup:**
    *   In `app/src/main/java/com/example/data/remote/ApiClient.kt`, ensure `BASE_URL` points to your backend.
    *   For local Android emulator, use `http://10.0.2.2:8000`.
    *   For physical devices, expose backend via ngrok or local network IP.

5.  **WireGuard Demo Server Configuration (Testing):**
    The demo node is no longer created automatically — startup used to delete and
    recreate it on every boot, discarding any edits made through the admin
    dashboard. Seed it explicitly, once:
    ```bash
    docker compose exec backend python -m backend.seed_demo
    ```
    Override the target with `DEMO_WG_ENDPOINT` / `DEMO_WG_PUBLIC_KEY`.

    > **Known limitation:** the client generates its own WireGuard keypair but has
    > no way to register the public half with the server, and every client
    > hardcodes the tunnel address `10.0.0.2/32`. Peer provisioning is Step 5 of
    > [ROADMAP.md](ROADMAP.md); until it lands, tunnels will not establish against
    > a correctly configured node.

## D) Environment Setup

All configuration lives in a single `.env` file at the **repository root** (not
in `/backend`). It is gitignored and never committed.

```bash
cp .env.example .env
```

Then fill in the required values. Generate a JWT secret with:

```bash
python -c "import secrets; print(secrets.token_urlsafe(48))"
```

`backend/config.py` validates configuration at import time. When `APP_ENV=production`
the application **refuses to start** if any of the following is true:

*   `JWT_SECRET_KEY` is unset, shorter than 32 characters, or matches a known
    placeholder that has previously appeared in this repository.
*   `DATABASE_URL` points at SQLite.
*   `CORS_ORIGINS` is empty.

In development, an ephemeral JWT secret is generated per process if none is set
(tokens will not survive a restart). There is no hardcoded fallback secret.

Interactive API docs (`/docs`, `/redoc`, `/openapi.json`) are automatically
disabled when `APP_ENV=production`.

*Admin credentials are NOT hardcoded in source. They are provisioned once, on
first boot, from `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD`.*

> **Note on existing deployments:** Postgres only applies `POSTGRES_PASSWORD` when
> initialising an empty data directory. If you are rotating the password against an
> existing volume, change it in the database as well:
> ```bash
> docker compose exec db psql -U vpn_admin -d shieldvpn -c "ALTER USER vpn_admin WITH PASSWORD 'new-password';"
> ```

## E) Testing Instructions

**Backend test suite** (80 tests). CI runs these on every push; locally:

```bash
pip install -r backend/requirements-dev.txt
ruff check backend/
pytest
```

### Manual test flows

1.  **Testing Guest Mode:**
    *   Open App -> Tap "Continue as Free User".
    *   Observe access to Free servers. Click a Premium server and observe the paywall restriction.
2.  **Testing Premium Flows:**
    *   Sign Up via the app.
    *   (Simulate IAP if enabled or modify DB) upgrade user to premium.
    *   Login and verify access to Premium nodes.
3.  **Testing WireGuard Connectivity:**
    *   Ensure VPN Service permissions are granted.
    *   Select the "Demo Server" (Frankfurt) and hit Connect.
    *   Observe VPN lock icon in status bar (success handshake).
4.  **Simulating Server Offline States:**
    *   Login to Admin dashboard (or modify DB manually).
    *   Change server status from `online` to `offline`.
    *   Refresh Mobile App -> Attempt connection -> Note rejection logic.

## F) Future Flutter Migration Notes

The current repository uses Android/Jetpack Compose, but the architecture strictly enables a future move to Flutter:

*   **Reusable Layers:** The entirely of the `/backend` (FastAPI, Postgres, Schemas) remains unchanged.
*   **Framework-Independent APIs:** The `/auth/` and `/servers/` JSON REST APIs can be consumed by Dart `http` or `dio` packages immediately.
*   **Recommended Flutter Architecture:**
    1.  Use `provider` or `riverpod` for State Management (analogous to Kotlin ViewModels).
    2.  Use `sqflite` for caching server lists (analogous to Room).
    3.  WireGuard integration in Flutter will require Platform Channels (`MethodChannel`) to interact with native iOS `NetworkExtension` and Android `VpnService` (which is already implemented natively in this repo in `WireGuardVpnService.kt`, ready to be invoked by Flutter).

## G) Database Migrations

The schema is owned by Alembic (`backend/migrations`), not by
`Base.metadata.create_all()`. The container entrypoint runs `alembic upgrade head`
before starting the API, so a normal `docker compose up` applies anything pending.

```bash
# Inspect current revision
docker compose exec backend alembic -c backend/alembic.ini current

# Create a new migration after changing backend/models.py
docker compose exec backend alembic -c backend/alembic.ini revision --autogenerate -m "describe change"

# Roll back one revision
docker compose exec backend alembic -c backend/alembic.ini downgrade -1
```

> **Upgrading a database created before Alembic was introduced:** its tables
> already exist but it has no version table, so the initial migration would fail.
> Mark it as already at the baseline first, once:
> ```bash
> docker compose exec backend alembic -c backend/alembic.ini stamp 0001
> ```

## H) Backoffice API

All routes require an admin bearer token from `POST /admin/auth/login`.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/admin/stats` | Headline counts for the dashboard |
| `GET` | `/admin/users` | List users — `limit`, `offset`, `search` |
| `GET` | `/admin/users/{id}` | Single user |
| `PATCH` | `/admin/users/{id}` | Set `is_admin` / `is_active` / `is_premium` |
| `DELETE` | `/admin/users/{id}` | Delete a user |
| `POST` | `/admin/users/{id}/subscription` | Grant or extend premium |
| `DELETE` | `/admin/users/{id}/subscription` | Revoke premium |
| `GET` | `/admin/servers` | List servers — `limit`, `offset` |
| `GET` | `/admin/servers/{id}` | Single server |
| `POST` | `/admin/servers` | Add a node (409 if the id exists) |
| `PUT` | `/admin/servers/{id}` | Partial update |
| `DELETE` | `/admin/servers/{id}` | Remove a node |

Guards worth knowing about: the last active admin cannot be demoted,
deactivated or deleted, and no admin can delete their own account. Renewing a
subscription extends from the existing expiry rather than truncating it; pass
`"extend": false` to replace the window instead.

## I) CI and the review agent

`.github/workflows/ci.yml` runs on every push and pull request with no setup:
backend lint and tests, dashboard typecheck/build/audit, a gitleaks secret scan,
and a Trivy scan of both container images.

`.github/workflows/claude-review.yml` reviews each pull request against
`ROADMAP.md`. It needs two things, and **skips itself cleanly if they are absent**
rather than failing every PR:

1. **Install the Claude GitHub App** on this repository — <https://github.com/apps/claude>.
   Without it the action returns `401 Claude Code is not installed on this repository`.
2. **Add `ANTHROPIC_API_KEY`** under Settings → Secrets and variables → Actions.

To make CI binding, mark the `Backend`, `Dashboard`, `Secret scan` and
`Container images` checks as required under Settings → Branches → branch
protection for `main`.
