# Shield VPN — Production Readiness Roadmap

Single source of truth for what "done" means. Each step is one PR. Check items
off in the PR that implements them.

**Status legend:** `[ ]` not started · `[~]` in progress · `[x]` done

---

## Step 1 — Make it boot, and make configuration safe `[x]`

*Verified: image builds, container reaches `healthy`, signup/login/`/users/me`
return 200, unauthenticated requests 401, non-admin hitting `/admin/users` 403,
seeded admin 200, and production config validation refuses all three bad-config
cases.*

The backend container could not start at all, and every Dockerised deployment
signed JWTs with a publicly-known key.

- [x] Fix `ModuleNotFoundError: No module named 'backend'` — the image copied
      source to `/app` while the code uses `from backend import ...`
- [x] Centralise config in `backend/config.py` with fail-fast validation
      (refuses to start in production with a weak/placeholder/absent JWT secret,
      SQLite, or empty CORS)
- [x] Fix the `SECRET_KEY` vs `JWT_SECRET_KEY` env-var mismatch that silently
      forced the hardcoded fallback key
- [x] `JWT_ALGORITHM` and `ACCESS_TOKEN_EXPIRE_MINUTES` are now actually honoured
      (were declared in compose, ignored by the app, hardcoded to 7 days)
- [x] Remove hardcoded DB password and JWT secret from `docker-compose.yml`
- [x] Stop publishing Postgres on the host by default
- [x] Add `/health` endpoint + container healthchecks + dependency ordering
- [x] Run the backend container as a non-root user
- [x] Stop deleting and recreating the demo server on every startup; move it to
      an explicit `backend/seed_demo.py`
- [x] Disable `/docs`, `/redoc`, `/openapi.json` in production
- [x] Untrack committed build artifacts (`app-debug.apk`, `debug.keystore.base64`)
      and extend `.gitignore`
- [x] Pin `bcrypt<4.1` (passlib 1.7.4 breaks against newer bcrypt)
- [ ] Rotate the demo WireGuard keypair and retire the ngrok tunnel *(needs owner)*

## Step 2 — Automated quality gate (the "QA agent") `[x]`

*Verified locally: ruff clean, 31 tests pass, dashboard `npm ci` + typecheck +
build + audit pass, gitleaks reports no leaks on a CI-equivalent checkout, and
both container images build.*

Everything below runs on every push with zero human involvement. This is the
foundation the review agent sits on.

- [x] `pytest` + `httpx` test suite for the backend (auth, authz, config
      validation, server CRUD) — 31 tests, up from zero
- [x] `ruff` lint + format check
- [x] `gitleaks` secret scan (would have caught every finding in Step 1)
- [x] `npm run build` + `tsc --noEmit` for the dashboard
- [x] Trivy scan of both container images
- [x] GitHub Actions workflow wiring the above to PRs
- [ ] Mark the CI checks as required to merge *(needs owner: repo settings)*
- [x] Claude Code review action on PRs, checking the diff against this roadmap
- [ ] Add `ANTHROPIC_API_KEY` to repository secrets *(needs owner)*

## Step 3 — Backend: the missing core feature `[ ]`

WireGuard is mutually key-authenticated. The client generates a keypair and
never sends the public half anywhere, and no endpoint exists to receive it — so
no tunnel can be established against a correctly configured node. Every client
also hardcodes the same tunnel IP `10.0.0.2/32`, so only one peer could ever
work.

- [ ] `POST /vpn/peers` — register a client public key, allocate a unique
      tunnel IP from a pool, return the full peer config
- [ ] `DELETE /vpn/peers/{id}` — revoke on disconnect/logout
- [ ] Peer table + per-user peer limits + IP allocation that survives restarts
- [ ] A control path that actually applies peers to the WireGuard node
      *(design depends on who owns the nodes — see Open Questions)*
- [ ] Require auth on `/servers` (currently public, leaks node keys/endpoints)
- [ ] Alembic migrations (`create_all()` will not apply schema changes)
- [ ] Rate limit `/auth/login`, `/auth/signup`, `/admin/auth/login`
- [ ] Email validation (`EmailStr`) and a password policy — signup currently
      accepts any string
- [ ] Structured logging + a global exception handler

## Step 4 — Android client hardening `[ ]`

- [ ] Move `BASE_URL` out of source into `BuildConfig` per build type
      (a personal ngrok tunnel is currently compiled into the APK)
- [ ] Strip `HttpLoggingInterceptor.Level.BODY` from release builds
      (leaks bearer tokens to logcat)
- [ ] Remove `android:usesCleartextTraffic="true"`
- [ ] Call the peer registration API from Step 3; drop the hardcoded tunnel IP
- [ ] Persist the auth token in EncryptedSharedPreferences (currently in-memory)
- [ ] Release signing config + Play Store VPN policy compliance review

## Step 5 — Admin dashboard: real integration `[ ]`

Currently a scaffold rendering one hardcoded row; the axios call is commented out.
Its dependency tree also did not resolve at all until Step 2 pinned TypeScript
back to 4.x, because react-scripts 5.0.1 rejects TS5 as a peer.

- [ ] Migrate off Create React App to Vite. CRA is deprecated and carries 14
      high-severity build-time advisories; once migrated, tighten the CI audit
      gate from `critical` to `high` and drop the TS4 pin
- [ ] Login screen against `/admin/auth/login`
- [ ] Auth context + token persistence + protected routes (`react-router-dom`
      is installed but unused)
- [ ] Real server and user management views
- [ ] API base URL via build-time env, nginx proxy for `/api`
- [ ] Error and loading states

## Step 6 — Deployment `[ ]`

- [ ] TLS termination + reverse proxy in front of the API
- [ ] Real domain, retire ngrok
- [ ] Resource limits, log aggregation, database backups
- [ ] Nightly scheduled agent run against the deployed stack

---

## Decisions

1. **WireGuard nodes: self-hosted VPS with root access.** Step 3 will therefore
   build a control agent on each node that applies peers via `wg set`, with the
   backend allocating tunnel IPs from a pool. Real multi-user support.
2. **Automation: CI + PR review agent**, not a bespoke multi-agent framework.
   Shared state is this repo; see Step 2.
3. **Backend deploys to managed cloud** — provider still to be named. Step 2 CI
   stays provider-agnostic (build/test/scan only); the deploy job lands in Step 6.

## Still open

- Which managed cloud provider? *(needed before Step 6, not before)*
- Is `us1-wg.ssl-tun.xyz:2600` a node you control, or a borrowed test endpoint?
  Its keypair is committed in git history and must be rotated regardless.
