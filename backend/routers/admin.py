"""Backoffice endpoints. Every route here requires an admin token."""
from fastapi import APIRouter, Depends, HTTPException
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.orm import Session

from backend import models
from backend.database import get_db
from backend.schemas import Server, TokenResponse, User
from backend.security import get_current_admin, issue_token_for, verify_password

router = APIRouter(prefix="/admin", tags=["admin"])


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
            or not user.is_admin):
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


@router.get("/users", response_model=list[User])
def admin_list_users(
    current_admin=Depends(get_current_admin),
    db: Session = Depends(get_db),
):
    return db.query(models.DbUser).all()


@router.post("/servers", response_model=Server)
def admin_add_server(
    server: Server,
    current_admin=Depends(get_current_admin),
    db: Session = Depends(get_db),
):
    db_server = models.DbServer(**server.model_dump())
    db.add(db_server)
    db.commit()
    db.refresh(db_server)
    return db_server
