"""Request and response models shared across routers."""
from datetime import datetime

from pydantic import BaseModel, ConfigDict


class Server(BaseModel):
    id: str
    country: str
    city: str
    ip_address: str
    is_premium: bool
    status: str
    load_percent: int
    wg_public_key: str | None = None
    wg_endpoint: str | None = None
    dns: str | None = "8.8.8.8"
    keepalive: int | None = 25

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
