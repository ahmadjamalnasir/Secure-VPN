from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base

from backend.config import settings

DATABASE_URL = settings.database_url

_is_sqlite = DATABASE_URL.startswith("sqlite")

engine = create_engine(
    DATABASE_URL,
    connect_args={"check_same_thread": False} if _is_sqlite else {},
    # Recycle connections before Postgres' default idle timeout and verify them
    # before handing them out, so a restarted database doesn't poison the pool.
    pool_pre_ping=not _is_sqlite,
    pool_recycle=1800 if not _is_sqlite else -1,
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base = declarative_base()


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
