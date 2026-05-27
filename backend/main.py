from fastapi import FastAPI, Depends, HTTPException, status
from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime
import uuid

app = FastAPI(
    title="Shield VPN API",
    description="Backend API for Shield VPN Mobile and Admin Dashboard",
    version="1.0.0"
)

# ---- Schemas ----
class Server(BaseModel):
    id: str
    country: str
    city: str
    ip_address: str
    is_premium: bool
    status: str
    load_percent: int
    wg_public_key: Optional[str] = None
    wg_endpoint: Optional[str] = None

class User(BaseModel):
    id: str
    email: str
    is_premium: bool
    subscription_expiry: Optional[datetime]

class LoginRequest(BaseModel):
    email: str
    password: str

# ---- Data ----
# Starting with NO mock data in the main pool, only a WireGuard demo server when requested.
SERVERS = []

WG_DEMO_SERVER = Server(
    id="wg-demo-1",
    country="Germany",
    city="Frankfurt",
    ip_address="198.51.100.1",
    is_premium=True,
    status="online",
    load_percent=15,
    wg_public_key="xTIBA5rboUvnH4htodjb6e69ziQtiZOVmF2LgZxwB2o=",
    wg_endpoint="198.51.100.1:51820"
)

# ---- Endpoints ----
@app.get("/")
def read_root():
    return {"status": "ok", "message": "Shield VPN API is running"}

@app.get("/servers", response_model=List[Server])
def list_servers():
    """Returns the list of available VPN servers."""
    return SERVERS  # Currently empty in production until populated

@app.get("/servers/wg-demo", response_model=Server)
def get_wg_demo():
    """Returns the actual WireGuard demo server configs."""
    return WG_DEMO_SERVER

@app.post("/auth/login")
def login(request: LoginRequest):
    """Authenticates user and returns JWT token."""
    if request.email == "admin" and request.password == "admin":
        return {"access_token": "admin-demo-token", "token_type": "bearer", "is_premium": True}
    if request.password == "password":
        return {"access_token": f"user-{uuid.uuid4()}", "token_type": "bearer", "is_premium": False}
    raise HTTPException(status_code=401, detail="Invalid credentials")

@app.get("/users/me", response_model=User)
def get_current_user():
    """Returns current authenticated user details."""
    return User(id="user-123", email="user@example.com", is_premium=True, subscription_expiry=datetime.utcnow())

@app.post("/subscriptions/verify")
def verify_receipt(receipt_data: str):
    """Verifies App Store / Google Play receipt and upgrades user."""
    return {"status": "success", "is_premium": True}

