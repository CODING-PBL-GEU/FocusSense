"""
FocusSense Central REST API Server
Technology Stack: Python 3.10+, FastAPI (Async), PostgreSQL / Supabase, Firebase Admin SDK (FCM)
"""

import os
import time
import uuid
from typing import List, Optional
from fastapi import FastAPI, HTTPException, Depends, Header, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from sqlalchemy import (
    create_engine, Column, String, BigInteger, Boolean, Float, Text, Integer, ForeignKey
)
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session

# ---------------------------------------------------------------------------
# Configuration & Database Connection
# ---------------------------------------------------------------------------
# Set your DATABASE_URL in your environment or use SQLite fallback for local testing
DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "sqlite:///./focussense_dev.db" # In production, set to Supabase: postgresql://postgres:password@db.xxxx.supabase.co:5432/postgres
)

# Convert postgres:// to postgresql:// for SQLAlchemy if needed
if DATABASE_URL.startswith("postgres://"):
    DATABASE_URL = DATABASE_URL.replace("postgres://", "postgresql://", 1)

engine = create_engine(
    DATABASE_URL,
    connect_args={"check_same_thread": False} if "sqlite" in DATABASE_URL else {}
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

# ---------------------------------------------------------------------------
# SQLAlchemy ORM Models (Matching ER Diagram)
# ---------------------------------------------------------------------------
class FamilyGroupModel(Base):
    __tablename__ = "family_groups"
    group_id = Column(String(64), primary_key=True, index=True)
    family_name = Column(String(128), nullable=False)
    created_at = Column(BigInteger, default=lambda: int(time.time() * 1000))

class UserModel(Base):
    __tablename__ = "users"
    user_id = Column(String(64), primary_key=True, index=True)
    group_id = Column(String(64), ForeignKey("family_groups.group_id"))
    email = Column(String(128), unique=True, nullable=False)
    password_hash = Column(String(256), nullable=False)
    role = Column(String(32), nullable=False) # 'parent' or 'child'
    name = Column(String(128), nullable=False)
    pin = Column(String(8), default="1234")
    avatar = Column(String(64), default="default")

class DeviceModel(Base):
    __tablename__ = "devices"
    device_id = Column(String(64), primary_key=True, index=True)
    user_id = Column(String(64), ForeignKey("users.user_id"))
    device_name = Column(String(128), nullable=False)
    token = Column(Text, nullable=True) # FCM push token
    battery_percent = Column(Integer, default=100)
    is_online = Column(Boolean, default=True)
    last_active = Column(BigInteger, default=lambda: int(time.time() * 1000))

class ActivityLogModel(Base):
    __tablename__ = "activity_logs"
    log_id = Column(String(64), primary_key=True, index=True)
    child_id = Column(String(64), ForeignKey("users.user_id"), index=True)
    package_name = Column(String(128), nullable=False)
    app_name = Column(String(128), nullable=False)
    content_title = Column(Text, nullable=True)
    extracted_text = Column(Text, nullable=False)
    is_flagged = Column(Boolean, default=False, index=True)
    threat_category = Column(String(64), nullable=True)
    confidence_score = Column(Float, default=0.0)
    ai_analysis_summary = Column(Text, nullable=True)
    recorded_at = Column(BigInteger, default=lambda: int(time.time() * 1000), index=True)
    is_synced = Column(Boolean, default=True)
    is_acknowledged = Column(Boolean, default=False)

class ScheduleRuleModel(Base):
    __tablename__ = "schedule_rules"
    rule_id = Column(String(64), primary_key=True, index=True)
    child_id = Column(String(64), ForeignKey("users.user_id"), index=True)
    rule_name = Column(String(128), nullable=False)
    category = Column(String(64), nullable=False)
    start_time = Column(String(10), nullable=False)
    end_time = Column(String(10), nullable=False)
    day_of_week = Column(String(64), default="Mon,Tue,Wed,Thu,Fri,Sat,Sun")
    restricted_packages = Column(Text, nullable=False)
    is_active = Column(Boolean, default=True)

class LocationPointModel(Base):
    __tablename__ = "location_history"
    loc_id = Column(String(64), primary_key=True, index=True)
    child_id = Column(String(64), ForeignKey("users.user_id"), index=True)
    latitude = Column(Float, nullable=False)
    longitude = Column(Float, nullable=False)
    accuracy = Column(Float, default=5.0)
    location_name = Column(String(128), default="Live GPS Fix")
    recorded_at = Column(BigInteger, default=lambda: int(time.time() * 1000), index=True)
    is_synced = Column(Boolean, default=True)

# Create tables
Base.metadata.create_all(bind=engine)

# ---------------------------------------------------------------------------
# Pydantic Schemas
# ---------------------------------------------------------------------------
class ActivityLogSchema(BaseModel):
    log_id: str
    child_id: str
    package_name: str
    app_name: str
    content_title: Optional[str] = ""
    extracted_text: str
    is_flagged: bool = False
    threat_category: Optional[str] = "Safe"
    confidence_score: float = 0.0
    ai_analysis_summary: Optional[str] = ""
    recorded_at: int
    is_synced: bool = True
    is_acknowledged: bool = False

class ScheduleRuleSchema(BaseModel):
    rule_id: str
    child_id: str
    rule_name: str
    category: str
    start_time: str
    end_time: str
    day_of_week: str
    restricted_packages: str
    is_active: bool = True

class LocationPointSchema(BaseModel):
    loc_id: str
    child_id: str
    latitude: float
    longitude: float
    accuracy: float = 5.0
    location_name: str = "Live GPS Fix"
    recorded_at: int

class DeviceRegistrationSchema(BaseModel):
    device_id: str
    user_id: str
    device_name: str
    token: Optional[str] = None
    battery_percent: int = 100

class SyncPayload(BaseModel):
    child_id: str
    logs: List[ActivityLogSchema] = []
    locations: List[LocationPointSchema] = []

# ---------------------------------------------------------------------------
# FastAPI App Initialization
# ---------------------------------------------------------------------------
app = FastAPI(
    title="FocusSense Parental Control & Safety API",
    description="Backend engine connecting parent and child devices with PostgreSQL / Supabase",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

# ---------------------------------------------------------------------------
# API Endpoints
# ---------------------------------------------------------------------------

@app.get("/api/health")
def health_check():
    return {
        "status": "healthy",
        "service": "FocusSense Python Engine",
        "timestamp": int(time.time() * 1000)
    }

# 1. Device Registration & Presence
@app.post("/api/devices/register")
def register_device(payload: DeviceRegistrationSchema, db: Session = Depends(get_db)):
    dev = db.query(DeviceModel).filter(DeviceModel.device_id == payload.device_id).first()
    if not dev:
        dev = DeviceModel(
            device_id=payload.device_id,
            user_id=payload.user_id,
            device_name=payload.device_name,
            token=payload.token,
            battery_percent=payload.battery_percent,
            is_online=True,
            last_active=int(time.time() * 1000)
        )
        db.add(dev)
    else:
        dev.token = payload.token
        dev.battery_percent = payload.battery_percent
        dev.is_online = True
        dev.last_active = int(time.time() * 1000)
    db.commit()
    return {"status": "registered", "device_id": dev.device_id}

@app.get("/api/devices/{user_id}")
def get_user_devices(user_id: str, db: Session = Depends(get_db)):
    return db.query(DeviceModel).filter(DeviceModel.user_id == user_id).all()

# 2. Activity Logs & Zero Data-Loss Sync (Child -> Server)
@app.post("/api/sync")
def sync_child_data(payload: SyncPayload, db: Session = Depends(get_db)):
    """Receives offline-queued logs and locations from Child devices."""
    saved_logs_count = 0
    flagged_alerts_count = 0

    for log_data in payload.logs:
        existing = db.query(ActivityLogModel).filter(ActivityLogModel.log_id == log_data.log_id).first()
        if not existing:
            new_log = ActivityLogModel(
                log_id=log_data.log_id,
                child_id=payload.child_id,
                package_name=log_data.package_name,
                app_name=log_data.app_name,
                content_title=log_data.content_title,
                extracted_text=log_data.extracted_text,
                is_flagged=log_data.is_flagged,
                threat_category=log_data.threat_category,
                confidence_score=log_data.confidence_score,
                ai_analysis_summary=log_data.ai_analysis_summary,
                recorded_at=log_data.recorded_at,
                is_synced=True,
                is_acknowledged=log_data.is_acknowledged
            )
            db.add(new_log)
            saved_logs_count += 1
            if log_data.is_flagged:
                flagged_alerts_count += 1
                # Trigger Push Notification to Parent devices here via Firebase FCM

    for loc in payload.locations:
        existing_loc = db.query(LocationPointModel).filter(LocationPointModel.loc_id == loc.loc_id).first()
        if not existing_loc:
            new_point = LocationPointModel(
                loc_id=loc.loc_id,
                child_id=payload.child_id,
                latitude=loc.latitude,
                longitude=loc.longitude,
                accuracy=loc.accuracy,
                location_name=loc.location_name,
                recorded_at=loc.recorded_at,
                is_synced=True
            )
            db.add(new_point)

    db.commit()
    return {
        "status": "success",
        "synced_logs": saved_logs_count,
        "flagged_threats": flagged_alerts_count,
        "synced_locations": len(payload.locations)
    }

# 3. Parent Queries: Read Activity Logs
@app.get("/api/logs/{child_id}")
def get_child_logs(child_id: str, flagged_only: bool = False, db: Session = Depends(get_db)):
    query = db.query(ActivityLogModel).filter(ActivityLogModel.child_id == child_id)
    if flagged_only:
        query = query.filter(ActivityLogModel.is_flagged == True)
    return query.order_by(ActivityLogModel.recorded_at.desc()).limit(100).all()

@app.delete("/api/logs/{log_id}")
def delete_vulnerable_log(log_id: str, db: Session = Depends(get_db)):
    """Allows Parent to purge vulnerable logs as requested in specifications."""
    log = db.query(ActivityLogModel).filter(ActivityLogModel.log_id == log_id).first()
    if not log:
        raise HTTPException(status_code=404, detail="Log not found")
    db.delete(log)
    db.commit()
    return {"status": "deleted", "log_id": log_id}

@app.put("/api/logs/{log_id}/acknowledge")
def acknowledge_log(log_id: str, db: Session = Depends(get_db)):
    log = db.query(ActivityLogModel).filter(ActivityLogModel.log_id == log_id).first()
    if not log:
        raise HTTPException(status_code=404, detail="Log not found")
    log.is_acknowledged = True
    db.commit()
    return {"status": "acknowledged", "log_id": log_id}

# 4. Schedule Rules (Parent modifies, Child reads)
@app.get("/api/schedules/{child_id}")
def get_schedules_for_child(child_id: str, db: Session = Depends(get_db)):
    return db.query(ScheduleRuleModel).filter(ScheduleRuleModel.child_id == child_id).all()

@app.post("/api/schedules")
def create_schedule_rule(rule: ScheduleRuleSchema, db: Session = Depends(get_db)):
    existing = db.query(ScheduleRuleModel).filter(ScheduleRuleModel.rule_id == rule.rule_id).first()
    if existing:
        for k, v in rule.dict().items():
            setattr(existing, k, v)
    else:
        new_rule = ScheduleRuleModel(**rule.dict())
        db.add(new_rule)
    db.commit()
    return {"status": "saved", "rule_id": rule.rule_id}

@app.delete("/api/schedules/{rule_id}")
def delete_schedule_rule(rule_id: str, db: Session = Depends(get_db)):
    rule = db.query(ScheduleRuleModel).filter(ScheduleRuleModel.rule_id == rule_id).first()
    if not rule:
        raise HTTPException(status_code=404, detail="Rule not found")
    db.delete(rule)
    db.commit()
    return {"status": "deleted", "rule_id": rule_id}

# 5. Live Location
@app.post("/api/location/report")
def report_child_location(point: LocationPointSchema, db: Session = Depends(get_db)):
    new_point = LocationPointModel(**point.dict())
    db.add(new_point)
    db.commit()
    return {"status": "recorded"}

@app.get("/api/location/{child_id}/latest")
def get_latest_child_location(child_id: str, db: Session = Depends(get_db)):
    point = db.query(LocationPointModel).filter(LocationPointModel.child_id == child_id)\
        .order_by(LocationPointModel.recorded_at.desc()).first()
    if not point:
        raise HTTPException(status_code=404, detail="No location recorded yet")
    return point

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
