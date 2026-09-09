"""Public authentication endpoints used by the mobile client."""
import uuid

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from backend import models
from backend.database import get_db
from backend.schemas import LoginRequest, TokenResponse, User
from backend.security import (
    get_current_user_from_token,
    get_password_hash,
    issue_token_for,
    verify_password,
)

router = APIRouter(tags=["auth"])


@router.post("/auth/signup")
def signup(request: LoginRequest, db: Session = Depends(get_db)):
    if db.query(models.DbUser).filter(models.DbUser.email == request.email).first():
        raise HTTPException(status_code=400, detail="Email already registered")

    new_user = models.DbUser(
        id=str(uuid.uuid4()),
        email=request.email,
        hashed_password=get_password_hash(request.password),
        is_premium=False,
        is_admin=False,
    )
    db.add(new_user)
    db.commit()

    return TokenResponse(
        access_token=issue_token_for(new_user),
        token_type="bearer",
        is_premium=new_user.is_premium,
        is_admin=False,
    )


@router.post("/auth/login", response_model=TokenResponse)
def login(request: LoginRequest, db: Session = Depends(get_db)):
    """Authenticates a normal user."""
    user = db.query(models.DbUser).filter(models.DbUser.email == request.email).first()
    if not user or not verify_password(request.password, user.hashed_password):
        raise HTTPException(status_code=401, detail="Invalid credentials")

    return TokenResponse(
        access_token=issue_token_for(user),
        token_type="bearer",
        is_premium=user.is_premium,
        is_admin=user.is_admin,
    )


@router.get("/users/me", response_model=User)
def get_current_user_info(current_user=Depends(get_current_user_from_token)):
    """Returns current authenticated user details."""
    return current_user
