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

3.  **Admin Dashboard Startup:**
    *(If running backoffice locally)*
    ```bash
    cd admin-dashboard
    npm install
    npm start
    ```

4.  **Mobile App Connection Setup:**
    *   In `app/src/main/java/com/example/data/remote/ApiClient.kt`, ensure `BASE_URL` points to your backend.
    *   For local Android emulator, use `http://10.0.2.2:8000`.
    *   For physical devices, expose backend via ngrok or local network IP.

5.  **WireGuard Demo Server Configuration (Testing):**
    The backend automatically seeds a WG demo server if one doesn't exist upon startup (`wg-demo-1` in Frankfurt).
    Use this server on the Mobile app to test the local VPN tunnel handshake.

## D) Environment Setup

Create a `.env` file in the `/backend` directory:

```env
DATABASE_URL=postgresql://postgres:postgres@db:5432/shieldvpn
# Fallback to SQLite if not using Docker:
# DATABASE_URL=sqlite:///./shieldvpn.db

JWT_SECRET_KEY=super-secret-production-key-here
SEED_ADMIN_EMAIL=admin@shieldvpn.local
SEED_ADMIN_PASSWORD=secure_initial_password_123
```

*Admin credentials are NOT hardcoded in source. They are provisioned only on first boot via these env variables.*

## E) Testing Instructions

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
