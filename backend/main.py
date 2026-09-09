"""Shield VPN API application."""
import uuid

from fastapi import Depends, FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text
from sqlalchemy.orm import Session

from backend import database, models
from backend.config import settings
from backend.database import get_db
from backend.routers import admin, auth, servers
from backend.security import get_password_hash

# The schema is owned by Alembic (backend/migrations); see entrypoint.sh.
# Tests create tables directly from the metadata in their own fixture.

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

app.include_router(auth.router)
app.include_router(servers.router)
app.include_router(admin.router)


@app.on_event("startup")
def setup_default_data():
    """Idempotent first-boot provisioning.

    Deliberately non-destructive: nothing here overwrites or deletes a row that
    an operator may have edited through the admin dashboard.
    """
    db = database.SessionLocal()
    try:
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
