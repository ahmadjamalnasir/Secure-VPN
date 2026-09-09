"""Centralised application configuration.

All runtime configuration is read from environment variables exactly once, here,
and validated at import time so that a misconfigured deployment fails fast at
startup instead of silently falling back to insecure defaults.
"""
import secrets
import warnings
from functools import lru_cache
from typing import Literal

from pydantic import Field, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

# Values that must never be used to sign tokens in production. These have all
# appeared in this repository's source or compose files at some point and are
# therefore public knowledge.
_BANNED_SECRETS = {
    "your-256-bit-secret-dev-key",
    "supersecretkey_change_in_production",
    "super-secret-production-key-here",
    "changeme",
    "secret",
}

MIN_SECRET_LENGTH = 32


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # ---- Environment ----
    app_env: Literal["development", "production"] = Field(
        default="development", alias="APP_ENV"
    )

    # ---- Database ----
    database_url: str = Field(
        default="sqlite:///./shieldvpn.db", alias="DATABASE_URL"
    )

    # ---- JWT ----
    jwt_secret_key: str = Field(default="", alias="JWT_SECRET_KEY")
    jwt_algorithm: str = Field(default="HS256", alias="JWT_ALGORITHM")
    access_token_expire_minutes: int = Field(
        default=60 * 24, alias="ACCESS_TOKEN_EXPIRE_MINUTES"
    )

    # ---- CORS ----
    # Comma-separated list of origins allowed to call the API from a browser.
    cors_origins: str = Field(default="", alias="CORS_ORIGINS")

    # ---- First-boot admin seeding (optional) ----
    seed_admin_email: str = Field(default="", alias="SEED_ADMIN_EMAIL")
    seed_admin_password: str = Field(default="", alias="SEED_ADMIN_PASSWORD")

    @property
    def is_production(self) -> bool:
        return self.app_env == "production"

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]

    @field_validator("database_url")
    @classmethod
    def _normalise_database_url(cls, v: str) -> str:
        return v.strip()

    @model_validator(mode="after")
    def _validate_production(self) -> "Settings":
        if self.is_production:
            problems = []

            if not self.jwt_secret_key:
                problems.append(
                    "JWT_SECRET_KEY is not set. Generate one with: "
                    "python -c \"import secrets; print(secrets.token_urlsafe(48))\""
                )
            elif self.jwt_secret_key in _BANNED_SECRETS:
                problems.append(
                    "JWT_SECRET_KEY is set to a known placeholder value that is "
                    "published in this repository. Generate a fresh one."
                )
            elif len(self.jwt_secret_key) < MIN_SECRET_LENGTH:
                problems.append(
                    f"JWT_SECRET_KEY must be at least {MIN_SECRET_LENGTH} characters "
                    f"(got {len(self.jwt_secret_key)})."
                )

            if self.database_url.startswith("sqlite"):
                problems.append(
                    "DATABASE_URL points at SQLite, which is not supported in "
                    "production. Use the PostgreSQL connection string."
                )

            if not self.cors_origin_list:
                problems.append(
                    "CORS_ORIGINS is empty. Set it to the admin dashboard origin, "
                    "e.g. https://admin.example.com"
                )

            if problems:
                raise RuntimeError(
                    "Refusing to start in production with invalid configuration:\n  - "
                    + "\n  - ".join(problems)
                )
        else:
            # Development: never fall back to a hardcoded constant. An ephemeral
            # per-process key is safe (tokens simply don't survive a restart).
            if not self.jwt_secret_key or self.jwt_secret_key in _BANNED_SECRETS:
                object.__setattr__(
                    self, "jwt_secret_key", secrets.token_urlsafe(48)
                )
                warnings.warn(
                    "JWT_SECRET_KEY not set; generated an ephemeral development "
                    "key. Tokens will be invalidated on restart. Set JWT_SECRET_KEY "
                    "in backend/.env to keep sessions stable across restarts.",
                    stacklevel=2,
                )

        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
