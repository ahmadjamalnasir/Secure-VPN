"""Configuration validation.

These guard the fix for the class of bug that shipped a publicly-known JWT
signing key to production. `_env_file=None` keeps a developer's local .env from
leaking into the assertions.
"""
import pytest

from backend.config import Settings

VALID_SECRET = "a" * 48
PROD_BASE = {
    "APP_ENV": "production",
    "DATABASE_URL": "postgresql://user:pass@db:5432/shieldvpn",
    "CORS_ORIGINS": "https://admin.example.com",
    "JWT_SECRET_KEY": VALID_SECRET,
}


def build(monkeypatch, **overrides):
    env = {**PROD_BASE, **overrides}
    for key in list(PROD_BASE) + ["JWT_ALGORITHM", "ACCESS_TOKEN_EXPIRE_MINUTES",
                                  "SEED_ADMIN_EMAIL", "SEED_ADMIN_PASSWORD"]:
        monkeypatch.delenv(key, raising=False)
    for key, value in env.items():
        if value is not None:
            monkeypatch.setenv(key, value)
    return Settings(_env_file=None)


def test_valid_production_config_is_accepted(monkeypatch):
    settings = build(monkeypatch)
    assert settings.is_production is True
    assert settings.jwt_secret_key == VALID_SECRET
    assert settings.cors_origin_list == ["https://admin.example.com"]


@pytest.mark.parametrize("banned", [
    "your-256-bit-secret-dev-key",        # the old hardcoded fallback
    "supersecretkey_change_in_production",  # the old docker-compose value
    "super-secret-production-key-here",   # the old README example
])
def test_production_refuses_known_placeholder_secrets(monkeypatch, banned):
    with pytest.raises(RuntimeError, match="placeholder"):
        build(monkeypatch, JWT_SECRET_KEY=banned)


def test_production_refuses_a_missing_secret(monkeypatch):
    with pytest.raises(RuntimeError, match="JWT_SECRET_KEY is not set"):
        build(monkeypatch, JWT_SECRET_KEY=None)


def test_production_refuses_a_short_secret(monkeypatch):
    with pytest.raises(RuntimeError, match="at least 32 characters"):
        build(monkeypatch, JWT_SECRET_KEY="tooshort")


def test_production_refuses_sqlite(monkeypatch):
    with pytest.raises(RuntimeError, match="SQLite"):
        build(monkeypatch, DATABASE_URL="sqlite:///./shieldvpn.db")


def test_production_refuses_empty_cors(monkeypatch):
    with pytest.raises(RuntimeError, match="CORS_ORIGINS is empty"):
        build(monkeypatch, CORS_ORIGINS=None)


def test_production_reports_every_problem_at_once(monkeypatch):
    """An operator should see the full list, not fix them one restart at a time."""
    with pytest.raises(RuntimeError) as exc:
        build(monkeypatch, JWT_SECRET_KEY=None,
              DATABASE_URL="sqlite:///./x.db", CORS_ORIGINS=None)
    message = str(exc.value)
    assert "JWT_SECRET_KEY is not set" in message
    assert "SQLite" in message
    assert "CORS_ORIGINS is empty" in message


def test_development_generates_an_ephemeral_secret_instead_of_a_constant(monkeypatch):
    first = build(monkeypatch, APP_ENV="development", JWT_SECRET_KEY=None)
    second = build(monkeypatch, APP_ENV="development", JWT_SECRET_KEY=None)
    assert len(first.jwt_secret_key) >= 32
    assert first.jwt_secret_key != second.jwt_secret_key, (
        "development must not fall back to a fixed constant"
    )


def test_development_replaces_a_banned_secret_rather_than_using_it(monkeypatch):
    settings = build(monkeypatch, APP_ENV="development",
                     JWT_SECRET_KEY="your-256-bit-secret-dev-key")
    assert settings.jwt_secret_key != "your-256-bit-secret-dev-key"


def test_cors_origins_are_split_and_trimmed(monkeypatch):
    settings = build(monkeypatch, CORS_ORIGINS="https://a.com, https://b.com ,")
    assert settings.cors_origin_list == ["https://a.com", "https://b.com"]
