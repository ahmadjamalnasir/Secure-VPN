"""Explicitly seed the shared WireGuard demo node used for local testing.

This used to run on every application startup, which deleted and recreated the
row each time and silently discarded any edits made through the admin
dashboard. It is now an opt-in one-off script:

    docker compose exec backend python -m backend.seed_demo

The endpoint below is a shared test node, not production infrastructure. Point
it at your own node with DEMO_WG_ENDPOINT / DEMO_WG_PUBLIC_KEY.
"""
import os
import sys

from backend import database, models

DEMO_ID = "wg-demo-1"
DEFAULT_ENDPOINT = "us1-wg.ssl-tun.xyz:2600"
DEFAULT_PUBLIC_KEY = "z1d6XOUyV2R0sEz211W5JpbFfsDc9wi7VCwvSgon+CA="


def main() -> int:
    endpoint = os.getenv("DEMO_WG_ENDPOINT", DEFAULT_ENDPOINT)
    public_key = os.getenv("DEMO_WG_PUBLIC_KEY", DEFAULT_PUBLIC_KEY)
    host = endpoint.rsplit(":", 1)[0]

    db = database.SessionLocal()
    try:
        existing = db.query(models.DbServer).filter(
            models.DbServer.id == DEMO_ID
        ).first()
        if existing:
            print(f"Demo server '{DEMO_ID}' already exists; leaving it untouched.")
            return 0

        db.add(models.DbServer(
            id=DEMO_ID,
            country="United States",
            city="Demo",
            ip_address=host,
            is_premium=False,
            status="online",
            load_percent=0,
            wg_public_key=public_key,
            wg_endpoint=endpoint,
            dns="8.8.8.8",
            keepalive=25,
        ))
        db.commit()
        print(f"Seeded demo server '{DEMO_ID}' at {endpoint}.")
        return 0
    finally:
        db.close()


if __name__ == "__main__":
    sys.exit(main())
