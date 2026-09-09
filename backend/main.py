import uuid
from datetime import datetime, timedelta

from fastapi import Depends, FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
from jose import JWTError, jwt
from passlib.context import CryptContext
from pydantic import BaseModel, ConfigDict
from sqlalchemy import text
from sqlalchemy.orm import Session

from backend import database, models
from backend.config import settings
from backend.database import engine, get_db

# Create tables
models.Base.metadata.create_all(bind=engine)

app = FastAPI(
    title="Shield VPN API",
    description="Backend API for Shield VPN Mobile and Admin Dashboard",
    version="1.0.0",
    # Interactive docs expose the full API surface; keep them off in production.
    docs_url=None if settings.is_production else "/docs",
    redoc_url=None if settings.is_production else "/redoc",
    openapi_url=None if settings.is_production else "/openapi.json",
)

if settings.cors_origin_list:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origin_list,
        allow_credentials=True,
        allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE"],
        allow_headers=["Authorization", "Content-Type"],
    )

# ---- Auth Setup ----
SECRET_KEY = settings.jwt_secret_key
ALGORITHM = settings.jwt_algorithm
ACCESS_TOKEN_EXPIRE_MINUTES = settings.access_token_expire_minutes

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/login")

def verify_password(plain_password, hashed_password):
    return pwd_context.verify(plain_password, hashed_password)

def get_password_hash(password):
    return pwd_context.hash(password)

def create_access_token(data: dict, expires_delta: timedelta | None = None):
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
        raise credentials_exception from None
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

# ---- Startup Event ----
@app.on_event("startup")
def setup_default_data():
    """Idempotent first-boot provisioning.

    Deliberately non-destructive: nothing here overwrites or deletes a row that
    an operator may have edited through the admin dashboard.
    """
    db = database.SessionLocal()
    try:
        # Seed the initial admin, once, from the environment.
        admin_email = settings.seed_admin_email
        admin_password = settings.seed_admin_password
        if admin_email and admin_password:
            existing_admin = db.query(models.DbUser).filter(
                models.DbUser.email == admin_email
            ).first()
            if not existing_admin:
                db.add(models.DbUser(
                    id=str(uuid.uuid4()),
                    email=admin_email,
                    hashed_password=get_password_hash(admin_password),
                    is_premium=True,
                    is_admin=True,
                ))
                db.commit()
    finally:
        db.close()


# ---- Endpoints: Public / Mobile ----
@app.get("/")
def read_root():
    return {"status": "ok", "message": "Shield VPN API is running"}

@app.get("/health")
def health(db: Session = Depends(get_db)):
    """Liveness + database readiness probe used by Docker and load balancers."""
    try:
        db.execute(text("SELECT 1"))
    except Exception as exc:
        raise HTTPException(status_code=503, detail="database unavailable") from exc
    return {"status": "healthy"}

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
    
    token = create_access_token(
        {"sub": new_user.email}, timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    )
    return TokenResponse(
        access_token=token,
        token_type="bearer",
        is_premium=new_user.is_premium,
        is_admin=False,
    )

@app.post("/auth/login", response_model=TokenResponse)
def login(request: LoginRequest, db: Session = Depends(get_db)):
    """Authenticates a normal user."""
    user = db.query(models.DbUser).filter(models.DbUser.email == request.email).first()
    if not user or not verify_password(request.password, user.hashed_password):
        raise HTTPException(status_code=401, detail="Invalid credentials")
    
    token = create_access_token(
        {"sub": user.email}, timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    )
    return TokenResponse(
        access_token=token,
        token_type="bearer",
        is_premium=user.is_premium,
        is_admin=user.is_admin,
    )

@app.get("/servers", response_model=list[Server])
def list_servers(db: Session = Depends(get_db)):
    """Returns the list of available VPN servers."""
    return db.query(models.DbServer).all()

@app.get("/servers/{server_id}", response_model=Server)
def get_server(server_id: str, db: Session = Depends(get_db)):
    """Returns the configs for a specific VPN server."""
    server = db.query(models.DbServer).filter(models.DbServer.id == server_id).first()
    if not server:
        raise HTTPException(status_code=404, detail="Server not found")
    return server

@app.get("/users/me", response_model=User)
def get_current_user_info(current_user = Depends(get_current_user_from_token)):
    """Returns current authenticated user details."""
    return current_user


# ---- Endpoints: Admin Backoffice ----
@app.post("/admin/auth/login", response_model=TokenResponse)
def admin_login(form_data: OAuth2PasswordRequestForm = Depends(), db: Session = Depends(get_db)):
    """Authenticates admin users (e.g. from React dashboard)."""
    user = db.query(models.DbUser).filter(
        models.DbUser.email == form_data.username
    ).first()
    if (not user
            or not verify_password(form_data.password, user.hashed_password)
            or not user.is_admin):
        raise HTTPException(
            status_code=401,
            detail="Invalid credentials or insufficient permissions",
        )

    token = create_access_token(
        {"sub": user.email}, timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    )
    return TokenResponse(
        access_token=token,
        token_type="bearer",
        is_premium=user.is_premium,
        is_admin=True,
    )

@app.get("/admin/users", response_model=list[User])
def admin_list_users(
    current_admin = Depends(get_current_admin),
    db: Session = Depends(get_db),
):
    return db.query(models.DbUser).all()

@app.post("/admin/servers", response_model=Server)
def admin_add_server(
    server: Server,
    current_admin = Depends(get_current_admin),
    db: Session = Depends(get_db),
):
    db_server = models.DbServer(**server.model_dump())
    db.add(db_server)
    db.commit()
    db.refresh(db_server)
    return db_server
