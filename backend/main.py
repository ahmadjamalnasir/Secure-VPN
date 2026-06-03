import os
import uuid
from datetime import datetime, timedelta
from typing import List, Optional

from fastapi import FastAPI, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
from pydantic import BaseModel
from sqlalchemy.orm import Session
from passlib.context import CryptContext
from jose import JWTError, jwt

from backend import models, database
from backend.database import engine, get_db

# Create tables
models.Base.metadata.create_all(bind=engine)

app = FastAPI(
    title="Shield VPN API",
    description="Backend API for Shield VPN Mobile and Admin Dashboard",
    version="1.0.0"
)

# ---- Auth Setup ----
SECRET_KEY = os.getenv("JWT_SECRET_KEY", "your-256-bit-secret-dev-key")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 60 * 24 * 7 # 7 days

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/login")

def verify_password(plain_password, hashed_password):
    return pwd_context.verify(plain_password, hashed_password)

def get_password_hash(password):
    return pwd_context.hash(password)

def create_access_token(data: dict, expires_delta: Optional[timedelta] = None):
    to_encode = data.copy()
    expire = datetime.utcnow() + (expires_delta if expires_delta else timedelta(minutes=15))
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)

def get_current_user_from_token(token: str = Depends(oauth2_scheme), db: Session = Depends(get_db)):
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        email: str = payload.get("sub")
        if email is None:
            raise credentials_exception
    except JWTError:
        raise credentials_exception
    user = db.query(models.DbUser).filter(models.DbUser.email == email).first()
    if user is None:
        raise credentials_exception
    return user

def get_current_admin(current_user=Depends(get_current_user_from_token)):
    if not current_user.is_admin:
        raise HTTPException(status_code=403, detail="Not authorized. Admin role required.")
    return current_user

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
    dns: Optional[str] = "8.8.8.8"
    keepalive: Optional[int] = 25
    
    class Config:
        from_attributes = True

class User(BaseModel):
    id: str
    email: str
    is_premium: bool
    is_admin: bool
    subscription_expiry: Optional[datetime]
    
    class Config:
        from_attributes = True

class LoginRequest(BaseModel):
    email: str
    password: str

class TokenResponse(BaseModel):
    access_token: str
    token_type: str
    is_premium: bool
    is_admin: bool

# ---- Startup Event ----
@app.on_event("startup")
def setup_default_data():
    db = database.SessionLocal()
    # 1. Seed Admin from Env
    admin_email = os.getenv("SEED_ADMIN_EMAIL")
    admin_password = os.getenv("SEED_ADMIN_PASSWORD")
    if admin_email and admin_password:
        existing_admin = db.query(models.DbUser).filter(models.DbUser.email == admin_email).first()
        if not existing_admin:
            new_admin = models.DbUser(
                id=str(uuid.uuid4()),
                email=admin_email,
                hashed_password=get_password_hash(admin_password),
                is_premium=True,
                is_admin=True
            )
            db.add(new_admin)
            
    # 2. Add or update WireGuard demo server
    wg_id = "wg-demo-1"
    existing_wg = db.query(models.DbServer).filter(models.DbServer.id == wg_id).first()
    if existing_wg:
        db.delete(existing_wg)
        db.commit()
    
    wg_server = models.DbServer(
        id=wg_id,
        country="United States",
        city="Demo",
        ip_address="us1-wg.ssl-tun.xyz",
        is_premium=False,
        status="online",
        load_percent=0,
        wg_public_key="z1d6XOUyV2R0sEz211W5JpbFfsDc9wi7VCwvSgon+CA=",
        wg_endpoint="us1-wg.ssl-tun.xyz:2600",
        dns="8.8.8.8",
        keepalive=25
    )
    db.add(wg_server)
        
    db.commit()
    db.close()


# ---- Endpoints: Public / Mobile ----
@app.get("/")
def read_root():
    return {"status": "ok", "message": "Shield VPN API is running"}

@app.post("/auth/signup")
def signup(request: LoginRequest, db: Session = Depends(get_db)):
    if db.query(models.DbUser).filter(models.DbUser.email == request.email).first():
        raise HTTPException(status_code=400, detail="Email already registered")
    
    user_id = str(uuid.uuid4())
    new_user = models.DbUser(
        id=user_id,
        email=request.email,
        hashed_password=get_password_hash(request.password),
        is_premium=False,
        is_admin=False
    )
    db.add(new_user)
    db.commit()
    
    token = create_access_token({"sub": new_user.email}, timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES))
    return TokenResponse(access_token=token, token_type="bearer", is_premium=new_user.is_premium, is_admin=False)

@app.post("/auth/login", response_model=TokenResponse)
def login(request: LoginRequest, db: Session = Depends(get_db)):
    """Authenticates a normal user."""
    user = db.query(models.DbUser).filter(models.DbUser.email == request.email).first()
    if not user or not verify_password(request.password, user.hashed_password):
        raise HTTPException(status_code=401, detail="Invalid credentials")
    
    token = create_access_token({"sub": user.email}, timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES))
    return TokenResponse(access_token=token, token_type="bearer", is_premium=user.is_premium, is_admin=user.is_admin)

@app.get("/servers", response_model=List[Server])
def list_servers(db: Session = Depends(get_db)):
    """Returns the list of available VPN servers."""
    return db.query(models.DbServer).all()

@app.get("/servers/wg-demo", response_model=Server)
def get_wg_demo(db: Session = Depends(get_db)):
    """Returns the actual WireGuard demo server configs."""
    server = db.query(models.DbServer).filter(models.DbServer.id == "wg-demo-1").first()
    if not server:
        raise HTTPException(status_code=404, detail="Demo server not found")
    return server

@app.get("/users/me", response_model=User)
def get_current_user_info(current_user = Depends(get_current_user_from_token)):
    """Returns current authenticated user details."""
    return current_user


# ---- Endpoints: Admin Backoffice ----
@app.post("/admin/auth/login", response_model=TokenResponse)
def admin_login(form_data: OAuth2PasswordRequestForm = Depends(), db: Session = Depends(get_db)):
    """Authenticates admin users (e.g. from React dashboard)."""
    user = db.query(models.DbUser).filter(models.DbUser.email == form_data.username).first()
    if not user or not verify_password(form_data.password, user.hashed_password) or not user.is_admin:
        raise HTTPException(status_code=401, detail="Invalid credentials or insufficient permissions")
    
    token = create_access_token({"sub": user.email}, timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES))
    return TokenResponse(access_token=token, token_type="bearer", is_premium=user.is_premium, is_admin=True)

@app.get("/admin/users", response_model=List[User])
def admin_list_users(current_admin = Depends(get_current_admin), db: Session = Depends(get_db)):
    return db.query(models.DbUser).all()

@app.post("/admin/servers", response_model=Server)
def admin_add_server(server: Server, current_admin = Depends(get_current_admin), db: Session = Depends(get_db)):
    db_server = models.DbServer(**server.dict())
    db.add(db_server)
    db.commit()
    db.refresh(db_server)
    return db_server
