"""Server catalogue endpoints consumed by the mobile client."""
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from backend import models
from backend.database import get_db
from backend.schemas import Server

router = APIRouter(tags=["servers"])


@router.get("/servers", response_model=list[Server])
def list_servers(db: Session = Depends(get_db)):
    """Returns the list of available VPN servers."""
    return db.query(models.DbServer).all()


@router.get("/servers/{server_id}", response_model=Server)
def get_server(server_id: str, db: Session = Depends(get_db)):
    """Returns the configs for a specific VPN server."""
    server = db.query(models.DbServer).filter(models.DbServer.id == server_id).first()
    if not server:
        raise HTTPException(status_code=404, detail="Server not found")
    return server
