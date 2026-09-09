"""Backoffice endpoints. Every route below /admin requires an admin token."""
from datetime import datetime, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.orm import Session

from backend import models
from backend.database import get_db
from backend.schemas import (
    Server,
    ServerPage,
    ServerUpdate,
    Stats,
    SubscriptionGrant,
    TokenResponse,
    UserAdmin,
    UserPage,
    UserUpdate,
)
from backend.security import get_current_admin, issue_token_for, verify_password

router = APIRouter(prefix="/admin", tags=["admin"])


def _get_user_or_404(db: Session, user_id: str) -> models.DbUser:
    user = db.query(models.DbUser).filter(models.DbUser.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return user


def _get_server_or_404(db: Session, server_id: str) -> models.DbServer:
    server = db.query(models.DbServer).filter(models.DbServer.id == server_id).first()
    if not server:
        raise HTTPException(status_code=404, detail="Server not found")
    return server


def _count_other_admins(db: Session, excluding_id: str) -> int:
    return (
        db.query(models.DbUser)
        .filter(
            models.DbUser.is_admin.is_(True),
            models.DbUser.is_active.is_(True),
            models.DbUser.id != excluding_id,
        )
        .count()
    )


# ---- Auth ----

@router.post("/auth/login", response_model=TokenResponse)
def admin_login(
    form_data: OAuth2PasswordRequestForm = Depends(),
    db: Session = Depends(get_db),
):
    """Authenticates admin users (e.g. from React dashboard)."""
    user = db.query(models.DbUser).filter(
        models.DbUser.email == form_data.username
    ).first()
    if (not user
            or not verify_password(form_data.password, user.hashed_password)
            or not user.is_admin
            or not user.is_active):
        raise HTTPException(
            status_code=401,
            detail="Invalid credentials or insufficient permissions",
        )

    return TokenResponse(
        access_token=issue_token_for(user),
        token_type="bearer",
        is_premium=user.is_premium,
        is_admin=True,
    )


# ---- Dashboard ----

@router.get("/stats", response_model=Stats)
def admin_stats(
    current_admin=Depends(get_current_admin),
    db: Session = Depends(get_db),
):
    """Headline counts for the dashboard landing page."""
    users = db.query(models.DbUser)
    servers = db.query(models.DbServer)
    now = datetime.utcnow()
    return Stats(
        total_users=users.count(),
        active_users=users.filter(models.DbUser.is_active.is_(True)).count(),
        premium_users=users.filter(
            models.DbUser.is_premium.is_(True),
            (models.DbUser.subscription_expiry.is_(None))
            | (models.DbUser.subscription_expiry > now),
        ).count(),
        admin_users=users.filter(models.DbUser.is_admin.is_(True)).count(),
        total_servers=servers.count(),
        online_servers=servers.filter(models.DbServer.status == "online").count(),
    )


# ---- Users ----

@router.get("/users", response_model=UserPage)
def admin_list_users(
    current_admin=Depends(get_current_admin),
    db: Session = Depends(get_db),
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    search: str | None = Query(default=None, max_length=200),
):
    query = db.query(models.DbUser)
    if search:
        query = query.filter(models.DbUser.email.ilike(f"%{search}%"))
    total = query.count()
    items = (
        query.order_by(models.DbUser.created_at.desc())
        .offset(offset)
        .limit(limit)
        .all()
    )
    return UserPage(total=total, limit=limit, offset=offset, items=items)


@router.get("/users/{user_id}", response_model=UserAdmin)
def admin_get_user(
    user_id: str,
    current_admin=Depends(get_current_admin),
    db: Session = Depends(get_db),
):
    return _get_user_or_404(db, user_id)


@router.patch("/users/{user_id}", response_model=UserAdmin)
def admin_update_user(
    user_id: str,
    changes: UserUpdate,
    current_admin=Depends(get_current_admin),
    db: Session = Depends(get_db),
):
    user = _get_user_or_404(db, user_id)
    updates = changes.model_dump(exclude_unset=True)

    # Lockout guards: an admin must not be able to remove the last way in,
    # including by locking out themselves.
    removes_admin_access = (
        updates.get("is_admin") is False or updates.get("is_active") is False
    )
    if user.is_admin and removes_admin_access and _count_other_admins(db, user.id) == 0:
        raise HTTPException(
            status_code=409,
            detail="Refusing to remove the last active admin.",
        )

    for field, value in updates.items():
        setattr(user, field, value)
    db.commit()
    db.refresh(user)
    return user


@router.delete("/users/{user_id}", status_code=204)
def admin_delete_user(
    user_id: str,
    current_admin=Depends(get_current_admin),
    db: Session = Depends(get_db),
):
    user = _get_user_or_404(db, user_id)
    if user.id == current_admin.id:
        raise HTTPException(
            status_code=409, detail="Refusing to delete your own account."
        )
    if user.is_admin and _count_other_admins(db, user.id) == 0:
        raise HTTPException(
            status_code=409, detail="Refusing to delete the last active admin."
        )
    db.delete(user)
    db.commit()


# ---- Subscriptions ----

@router.post("/users/{user_id}/subscription", response_model=UserAdmin)
def admin_grant_subscription(
    user_id: str,
    grant: SubscriptionGrant,
    current_admin=Depends(get_current_admin),
    db: Session = Depends(get_db),
):
    """Grant or extend premium access."""
    user = _get_user_or_404(db, user_id)
    now = datetime.utcnow()

    start = now
    if grant.extend and user.subscription_expiry and user.subscription_expiry > now:
        start = user.subscription_expiry

    user.subscription_expiry = start + timedelta(days=grant.duration_days)
    user.subscription_plan = grant.plan
    user.is_premium = True
    db.commit()
    db.refresh(user)
    return user


@router.delete("/users/{user_id}/subscription", response_model=UserAdmin)
def admin_revoke_subscription(
    user_id: str,
    current_admin=Depends(get_current_admin),
    db: Session = Depends(get_db),
):
    """Revoke premium access immediately."""
    user = _get_user_or_404(db, user_id)
    user.is_premium = False
    user.subscription_expiry = None
    user.subscription_plan = None
    db.commit()
    db.refresh(user)
    return user


# ---- Servers ----

@router.get("/servers", response_model=ServerPage)
def admin_list_servers(
    current_admin=Depends(get_current_admin),
    db: Session = Depends(get_db),
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
):
    query = db.query(models.DbServer)
    total = query.count()
    items = query.order_by(models.DbServer.id).offset(offset).limit(limit).all()
    return ServerPage(total=total, limit=limit, offset=offset, items=items)


@router.get("/servers/{server_id}", response_model=Server)
def admin_get_server(
    server_id: str,
    current_admin=Depends(get_current_admin),
    db: Session = Depends(get_db),
):
    return _get_server_or_404(db, server_id)


@router.post("/servers", response_model=Server, status_code=201)
def admin_add_server(
    server: Server,
    current_admin=Depends(get_current_admin),
    db: Session = Depends(get_db),
):
    if db.query(models.DbServer).filter(models.DbServer.id == server.id).first():
        raise HTTPException(
            status_code=409, detail=f"Server '{server.id}' already exists"
        )
    db_server = models.DbServer(**server.model_dump())
    db.add(db_server)
    db.commit()
    db.refresh(db_server)
    return db_server


@router.put("/servers/{server_id}", response_model=Server)
def admin_update_server(
    server_id: str,
    changes: ServerUpdate,
    current_admin=Depends(get_current_admin),
    db: Session = Depends(get_db),
):
    server = _get_server_or_404(db, server_id)
    for field, value in changes.model_dump(exclude_unset=True).items():
        setattr(server, field, value)
    db.commit()
    db.refresh(server)
    return server


@router.delete("/servers/{server_id}", status_code=204)
def admin_delete_server(
    server_id: str,
    current_admin=Depends(get_current_admin),
    db: Session = Depends(get_db),
):
    server = _get_server_or_404(db, server_id)
    db.delete(server)
    db.commit()
