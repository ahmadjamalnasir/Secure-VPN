from datetime import datetime

from sqlalchemy import Boolean, Column, DateTime, Integer, String

from backend.database import Base


class DbUser(Base):
    __tablename__ = "users"
    id = Column(String, primary_key=True, index=True)
    email = Column(String, unique=True, index=True)
    hashed_password = Column(String)
    is_premium = Column(Boolean, default=False)
    is_admin = Column(Boolean, default=False)
    subscription_expiry = Column(DateTime, nullable=True)
    # Which plan granted the current premium window, for reporting and support.
    subscription_plan = Column(String, nullable=True)
    # Soft disable: keeps the row (and its audit trail) while blocking login.
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)


class DbServer(Base):
    __tablename__ = "servers"
    id = Column(String, primary_key=True, index=True)
    country = Column(String)
    city = Column(String)
    ip_address = Column(String)
    is_premium = Column(Boolean, default=False)
    status = Column(String, default="online")
    load_percent = Column(Integer, default=0)
    wg_public_key = Column(String, nullable=True)
    wg_endpoint = Column(String, nullable=True)
    dns = Column(String, default="8.8.8.8", nullable=True)
    keepalive = Column(Integer, default=25, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(
        DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False
    )
