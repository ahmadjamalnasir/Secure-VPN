"""Test fixtures.

Environment is configured *before* importing anything from `backend`, because
`backend.config` validates and freezes settings at import time and
`backend.main` builds the engine and creates tables on import.
"""
import os
import tempfile
from pathlib import Path

_TEST_DB = Path(tempfile.gettempdir()) / "shieldvpn_pytest.db"

os.environ["APP_ENV"] = "development"
os.environ["DATABASE_URL"] = f"sqlite:///{_TEST_DB}"
os.environ["JWT_SECRET_KEY"] = "pytest-secret-key-long-enough-to-pass-validation"
os.environ["JWT_ALGORITHM"] = "HS256"
os.environ["ACCESS_TOKEN_EXPIRE_MINUTES"] = "60"
os.environ["CORS_ORIGINS"] = "http://localhost:3000"
# Seeding is exercised explicitly in its own test, never implicitly.
os.environ.pop("SEED_ADMIN_EMAIL", None)
os.environ.pop("SEED_ADMIN_PASSWORD", None)

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from backend import models  # noqa: E402
from backend.database import Base, SessionLocal, engine  # noqa: E402
from backend.main import app, get_password_hash  # noqa: E402


@pytest.fixture(autouse=True)
def clean_database():
    """Every test starts against empty tables."""
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    yield
    Base.metadata.drop_all(bind=engine)


@pytest.fixture
def client():
    return TestClient(app)


@pytest.fixture
def db():
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()


@pytest.fixture
def make_user(db):
    """Create a user directly, bypassing the signup endpoint."""
    def _make(email="user@test.local", password="password123",
              is_admin=False, is_premium=False):
        user = models.DbUser(
            id=f"id-{email}",
            email=email,
            hashed_password=get_password_hash(password),
            is_admin=is_admin,
            is_premium=is_premium,
        )
        db.add(user)
        db.commit()
        return user
    return _make


@pytest.fixture
def user_token(client, make_user):
    make_user(email="user@test.local", password="password123")
    resp = client.post("/auth/login", json={
        "email": "user@test.local", "password": "password123",
    })
    return resp.json()["access_token"]


@pytest.fixture
def admin_token(client, make_user):
    make_user(email="admin@test.local", password="adminpass123", is_admin=True)
    resp = client.post("/admin/auth/login", data={
        "username": "admin@test.local", "password": "adminpass123",
    })
    return resp.json()["access_token"]
