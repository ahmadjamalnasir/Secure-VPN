"""Request and response models shared across routers."""
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class Server(BaseModel):
    id: str = Field(min_length=1, max_length=64)
    country: str
    city: str
    ip_address: str
    is_premium: bool
    status: str
    # Same bounds as ServerUpdate, so a value rejected on update cannot be
    # smuggled in on create.
    load_percent: int = Field(ge=0, le=100)
    wg_public_key: str | None = None
    wg_endpoint: str | None = None
    dns: str | None = "8.8.8.8"
    keepalive: int | None = Field(default=25, ge=0, le=3600)

    model_config = ConfigDict(from_attributes=True)


class User(BaseModel):
    id: str
    email: str
    is_premium: bool
    is_admin: bool
    subscription_expiry: datetime | None

    model_config = ConfigDict(from_attributes=True)


class LoginRequest(BaseModel):
    email: str
    password: str


class TokenResponse(BaseModel):
    access_token: str
    token_type: str
    is_premium: bool
    is_admin: bool


# ---- Admin views ----

class UserAdmin(BaseModel):
    """Fuller user projection, exposed only to the backoffice."""
    id: str
    email: str
    is_premium: bool
    is_admin: bool
    is_active: bool
    subscription_plan: str | None
    subscription_expiry: datetime | None
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class UserUpdate(BaseModel):
    """Partial update. Omitted fields are left unchanged."""
    is_admin: bool | None = None
    is_active: bool | None = None
    is_premium: bool | None = None


class ServerUpdate(BaseModel):
    """Partial update. Omitted fields are left unchanged."""
    country: str | None = None
    city: str | None = None
    ip_address: str | None = None
    is_premium: bool | None = None
    status: str | None = None
    load_percent: int | None = Field(default=None, ge=0, le=100)
    wg_public_key: str | None = None
    wg_endpoint: str | None = None
    dns: str | None = None
    keepalive: int | None = Field(default=None, ge=0, le=3600)


class SubscriptionGrant(BaseModel):
    """Grant or extend premium access."""
    plan: str = Field(min_length=1, max_length=64)
    duration_days: int = Field(gt=0, le=3650)
    # Extend from the current expiry when the user still has time left,
    # rather than truncating it to now + duration.
    extend: bool = True


class Stats(BaseModel):
    total_users: int
    active_users: int
    premium_users: int
    admin_users: int
    total_servers: int
    online_servers: int


class Page(BaseModel):
    total: int
    limit: int
    offset: int


class UserPage(Page):
    items: list[UserAdmin]


class ServerPage(Page):
    items: list[Server]
