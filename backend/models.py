from sqlalchemy import Column, Integer, String, Boolean, DateTime
from backend.database import Base
from datetime import datetime

class DbUser(Base):
    __tablename__ = "users"
    id = Column(String, primary_key=True, index=True)
    email = Column(String, unique=True, index=True)
    hashed_password = Column(String)
    is_premium = Column(Boolean, default=False)
    is_admin = Column(Boolean, default=False)
    subscription_expiry = Column(DateTime, nullable=True)

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
