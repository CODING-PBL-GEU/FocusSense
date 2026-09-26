"""
FocusSense Central REST API & Load-Balancing Server
Technology Stack: Python 3.10+, FastAPI (Async), PostgreSQL / Supabase, DeepSeek LLM Inference Router
"""

import os
import re
import time
import uuid
import json
from typing import List, Optional

try:
    import httpx
except ImportError:
    httpx = None

try:
    import requests
except ImportError:
    requests = None

from fastapi import FastAPI, HTTPException, Depends, Header, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from sqlalchemy import (
    create_engine, Column, String, BigInteger, Boolean, Float, Text, Integer, ForeignKey
)
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session

from dotenv import load_dotenv
load_dotenv()

# ---------------------------------------------------------------------------
# Configuration & Database Connection
# ---------------------------------------------------------------------------
DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql://postgres.jlmyesmofptnrthetvpt:%23SKravi240211964@aws-0-ap-south-1.pooler.supabase.com:5432/postgres"
)

# DeepSeek Inference Server URL (e.g. vLLM or Ollama instance)
# Format for vLLM: http://your-gpu-server:8000/v1/chat/completions
# Format for Ollama: http://your-gpu-server:11434/api/chat
DEEPSEEK_SERVER_URL = os.getenv("DEEPSEEK_SERVER_URL", "")
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

# Convert postgres:// or postgresql:// to explicit driver for SQLAlchemy
if DATABASE_URL.startswith("postgres://"):
    DATABASE_URL = DATABASE_URL.replace("postgres://", "postgresql+psycopg2://", 1)
elif DATABASE_URL.startswith("postgresql://") and not DATABASE_URL.startswith("postgresql+"):
    DATABASE_URL = DATABASE_URL.replace("postgresql://", "postgresql+psycopg2://", 1)

# CRITICAL IPv4 COMPATIBILITY FOR RENDER & CLOUD CONTAINERS:
# Direct Supabase domain 'db.<project>.supabase.co' resolves strictly to IPv6.
# Render does NOT support outbound IPv6, leading to 'Network is unreachable (2406:da14...)'.
# Automatically rewrite to Supabase's IPv4 Connection Pooler:
if "db.jlmyesmofptnrthetvpt.supabase.co" in DATABASE_URL:
    DATABASE_URL = DATABASE_URL.replace("db.jlmyesmofptnrthetvpt.supabase.co:5432", "aws-0-ap-south-1.pooler.supabase.com:5432")
    DATABASE_URL = DATABASE_URL.replace("postgres:%23SKravi240211964", "postgres.jlmyesmofptnrthetvpt:%23SKravi240211964")
    DATABASE_URL = DATABASE_URL.replace("postgres:#SKravi240211964", "postgres.jlmyesmofptnrthetvpt:%23SKravi240211964")

# Supabase strictly requires SSL connections
if ("supabase.co" in DATABASE_URL or "supabase.com" in DATABASE_URL) and "sslmode" not in DATABASE_URL:
    separator = "&" if "?" in DATABASE_URL else "?"
    DATABASE_URL = f"{DATABASE_URL}{separator}sslmode=require"

engine = create_engine(
    DATABASE_URL,
    pool_pre_ping=True,      # Automatically reconnects if connection was dropped by cloud pooler
    pool_recycle=300,        # Recycles connections every 5 minutes to prevent stale sockets
    connect_args={"check_same_thread": False} if "sqlite" in DATABASE_URL else {}
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

# ---------------------------------------------------------------------------
# SQLAlchemy ORM Models (Matching Supabase Schema)
# ---------------------------------------------------------------------------
class FamilyGroupModel(Base):
    __tablename__ = "family_groups"
    group_id = Column(String(64), primary_key=True, index=True)
    family_name = Column(String(128), nullable=False)
    created_at = Column(BigInteger, default=lambda: int(time.time() * 1000))

class UserModel(Base):
    __tablename__ = "users"
    user_id = Column(String(64), primary_key=True, index=True)
    group_id = Column(String(64), ForeignKey("family_groups.group_id"), index=True)
    email = Column(String(128), unique=True, nullable=False, index=True)
    password_hash = Column(String(256), nullable=False)
    role = Column(String(32), nullable=False) # 'parent' or 'child'
    name = Column(String(128), nullable=False)
    pin = Column(String(8), default="1234")
    avatar = Column(String(64), default="default")

class DeviceModel(Base):
    __tablename__ = "devices"
    device_id = Column(String(64), primary_key=True, index=True)
    user_id = Column(String(64), ForeignKey("users.user_id"), index=True)
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

# Create tables in PostgreSQL / Supabase if not already present
try:
    Base.metadata.create_all(bind=engine)
except Exception as e:
    print(f"[FocusSense] Note: Table creation handled via schema.sql or deferred: {e}")

# ---------------------------------------------------------------------------
# Pydantic Schemas
# ---------------------------------------------------------------------------
class RegisterParentRequest(BaseModel):
    name: str
    family_name: Optional[str] = ""
    email: str
    password: str
    pin: Optional[str] = "1234"

class LoginParentRequest(BaseModel):
    email: str
    password: str

class PairChildRequest(BaseModel):
    parent_email: str
    parent_password: str
    child_name: str
    device_name: Optional[str] = "Child Phone"

class UserResponse(BaseModel):
    user_id: str
    group_id: str
    email: str
    role: str
    name: str
    pin: str
    avatar: str

class AuthResponse(BaseModel):
    status: str
    user: UserResponse
    family_name: str
    children: List[UserResponse] = []

class PairChildResponse(BaseModel):
    status: str
    child_user: UserResponse
    device_id: str
    group_id: str

class DeviceRegistrationSchema(BaseModel):
    device_id: str
    user_id: str
    device_name: str
    token: Optional[str] = None
    battery_percent: int = 100

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

class SyncPayload(BaseModel):
    child_id: str
    logs: List[ActivityLogSchema] = []
    locations: List[LocationPointSchema] = []

class ThreatEvaluateRequest(BaseModel):
    child_id: str
    package_name: str
    app_name: str
    content_title: Optional[str] = ""
    extracted_text: str

class ThreatEvaluateResponse(BaseModel):
    threat_detected: bool
    threat_category: str
    confidence_score: float
    ai_analysis_summary: str
    model_used: str

# ---------------------------------------------------------------------------
# FastAPI App Initialization & CORS
# ---------------------------------------------------------------------------
app = FastAPI(
    title="FocusSense Parental Control & Safety API",
    description="Central backend connecting Parent and Child devices with Supabase PostgreSQL and DeepSeek LLM routing.",
    version="2.0.0"
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
# Health & Status
# ---------------------------------------------------------------------------
@app.get("/api/health")
def health_check(db: Session = Depends(get_db)):
    db_status = "connected"
    counts = {}
    try:
        from sqlalchemy import text
        db.execute(text("SELECT 1"))
        # Auto-create tables in Supabase if not already present
        try:
            Base.metadata.create_all(bind=engine)
        except Exception:
            pass
        counts = {
            "users": db.query(UserModel).count(),
            "activity_logs": db.query(ActivityLogModel).count(),
            "devices": db.query(DeviceModel).count(),
            "schedule_rules": db.query(ScheduleRuleModel).count(),
            "location_points": db.query(LocationPointModel).count()
        }
    except Exception as e:
        db_status = f"error: {str(e)}"

    return {
        "status": "healthy" if "error" not in db_status else "database_connection_issue",
        "service": "FocusSense Central Engine",
        "deepseek_configured": bool(DEEPSEEK_SERVER_URL),
        "database": db_status,
        "supabase_counts": counts,
        "timestamp": int(time.time() * 1000)
    }

@app.get("/api/admin/init-db")
def initialize_database(db: Session = Depends(get_db)):
    """Explicit endpoint to create all tables in Supabase PostgreSQL and return table list."""
    try:
        from sqlalchemy import text
        Base.metadata.create_all(bind=engine)
        tables = db.execute(text("SELECT table_name FROM information_schema.tables WHERE table_schema='public'")).fetchall()
        return {
            "status": "success",
            "message": "All tables created successfully in Supabase PostgreSQL!",
            "tables": [t[0] for t in tables],
            "counts": {
                "users": db.query(UserModel).count(),
                "activity_logs": db.query(ActivityLogModel).count(),
                "devices": db.query(DeviceModel).count(),
                "schedule_rules": db.query(ScheduleRuleModel).count(),
                "location_points": db.query(LocationPointModel).count()
            }
        }
    except Exception as e:
        return {
            "status": "error",
            "error_type": type(e).__name__,
            "message": str(e),
            "hint": "Ensure DATABASE_URL has sslmode=require and credentials are valid."
        }

# ---------------------------------------------------------------------------
# 1. Authentication & Device Pairing
# ---------------------------------------------------------------------------
@app.post("/api/auth/register", response_model=AuthResponse)
def register_parent(payload: RegisterParentRequest, db: Session = Depends(get_db)):
    normalized_email = payload.email.strip().lower()
    existing = db.query(UserModel).filter(UserModel.email == normalized_email).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"An account with email '{normalized_email}' already exists. Please sign in."
        )

    # 1. Create Family Group
    group_id = f"group-{uuid.uuid4().hex[:8]}"
    family_name = payload.family_name.strip() if payload.family_name else f"{payload.name.strip()}'s Family"
    group = FamilyGroupModel(
        group_id=group_id,
        family_name=family_name
    )
    db.add(group)

    # 2. Create Parent User
    user_id = f"parent-{uuid.uuid4().hex[:8]}"
    parent = UserModel(
        user_id=user_id,
        group_id=group_id,
        email=normalized_email,
        password_hash=payload.password,
        role="parent",
        name=payload.name.strip(),
        pin=payload.pin.strip() if payload.pin else "1234",
        avatar="parent_avatar"
    )
    db.add(parent)

    # 3. Create Default Parent Device
    device_id = f"dev-{uuid.uuid4().hex[:8]}"
    dev = DeviceModel(
        device_id=device_id,
        user_id=user_id,
        device_name=f"{payload.name.strip()}'s Parent Phone",
        is_online=True,
        battery_percent=100
    )
    db.add(dev)
    db.commit()

    return AuthResponse(
        status="success",
        user=UserResponse(
            user_id=parent.user_id,
            group_id=parent.group_id,
            email=parent.email,
            role=parent.role,
            name=parent.name,
            pin=parent.pin,
            avatar=parent.avatar
        ),
        family_name=family_name,
        children=[]
    )

@app.post("/api/auth/login", response_model=AuthResponse)
def login_parent(payload: LoginParentRequest, db: Session = Depends(get_db)):
    normalized_email = payload.email.strip().lower()
    user = db.query(UserModel).filter(UserModel.email == normalized_email).first()
    if not user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"No account found for '{normalized_email}'."
        )
    if user.password_hash != payload.password:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect password. Please verify and try again."
        )

    group = db.query(FamilyGroupModel).filter(FamilyGroupModel.group_id == user.group_id).first()
    family_name = group.family_name if group else "My Family"

    # Fetch all children in this family
    children_models = db.query(UserModel).filter(
        UserModel.group_id == user.group_id,
        UserModel.role == "child"
    ).all()

    children = [
        UserResponse(
            user_id=c.user_id,
            group_id=c.group_id,
            email=c.email,
            role=c.role,
            name=c.name,
            pin=c.pin,
            avatar=c.avatar
        ) for c in children_models
    ]

    return AuthResponse(
        status="success",
        user=UserResponse(
            user_id=user.user_id,
            group_id=user.group_id,
            email=user.email,
            role=user.role,
            name=user.name,
            pin=user.pin,
            avatar=user.avatar
        ),
        family_name=family_name,
        children=children
    )

@app.post("/api/devices/pair", response_model=PairChildResponse)
def pair_child_device(payload: PairChildRequest, db: Session = Depends(get_db)):
    """Pairs a child's phone using parent credentials."""
    normalized_email = payload.parent_email.strip().lower()
    parent = db.query(UserModel).filter(
        UserModel.email == normalized_email,
        UserModel.role == "parent"
    ).first()

    if not parent:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Parent account not found. Please create the parent account first."
        )

    if parent.password_hash != payload.parent_password:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Parent password incorrect. Unable to link child device."
        )

    # Create new Child Profile in parent's group
    child_id = f"child-{uuid.uuid4().hex[:8]}"
    child_clean_name = payload.child_name.strip()
    child_email = f"{child_clean_name.lower().replace(' ', '')}-{uuid.uuid4().hex[:4]}@family.focussense"

    child = UserModel(
        user_id=child_id,
        group_id=parent.group_id,
        email=child_email,
        password_hash=parent.password_hash,
        role="child",
        name=child_clean_name,
        pin="",
        avatar="default_child"
    )
    db.add(child)

    # Register child device
    device_id = f"dev-{uuid.uuid4().hex[:8]}"
    dev = DeviceModel(
        device_id=device_id,
        user_id=child_id,
        device_name=payload.device_name.strip() if payload.device_name else f"{child_clean_name}'s Phone",
        is_online=True,
        battery_percent=100
    )
    db.add(dev)
    db.commit()

    return PairChildResponse(
        status="paired",
        child_user=UserResponse(
            user_id=child.user_id,
            group_id=child.group_id,
            email=child.email,
            role=child.role,
            name=child.name,
            pin=child.pin,
            avatar=child.avatar
        ),
        device_id=device_id,
        group_id=parent.group_id
    )

# ---------------------------------------------------------------------------
# 2. Hardware Devices & Presence Management
# ---------------------------------------------------------------------------
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
        dev.device_name = payload.device_name
        dev.token = payload.token
        dev.battery_percent = payload.battery_percent
        dev.is_online = True
        dev.last_active = int(time.time() * 1000)
    db.commit()
    return {"status": "registered", "device_id": dev.device_id}

@app.get("/api/devices/{user_id}", response_model=List[DeviceRegistrationSchema])
def get_user_devices(user_id: str, db: Session = Depends(get_db)):
    devs = db.query(DeviceModel).filter(DeviceModel.user_id == user_id).all()
    return [
        DeviceRegistrationSchema(
            device_id=d.device_id,
            user_id=d.user_id,
            device_name=d.device_name,
            token=d.token,
            battery_percent=d.battery_percent
        ) for d in devs
    ]

@app.get("/api/family/{group_id}/children", response_model=List[UserResponse])
def get_family_children(group_id: str, db: Session = Depends(get_db)):
    children = db.query(UserModel).filter(
        UserModel.group_id == group_id,
        UserModel.role == "child"
    ).all()
    return [
        UserResponse(
            user_id=c.user_id,
            group_id=c.group_id,
            email=c.email,
            role=c.role,
            name=c.name,
            pin=c.pin,
            avatar=c.avatar
        ) for c in children
    ]

# ---------------------------------------------------------------------------
# 3. DeepSeek AI Load Balancer & Vulnerability Evaluation
# ---------------------------------------------------------------------------
@app.post("/api/ai/evaluate", response_model=ThreatEvaluateResponse)
async def evaluate_content_threat(payload: ThreatEvaluateRequest, db: Session = Depends(get_db)):
    """
    Acts as router / load balancer to the dedicated DeepSeek AI inference server.
    If DEEPSEEK_SERVER_URL is configured, calls the self-hosted model.
    Otherwise, applies instant high-accuracy classification rules.
    """
    text = payload.extracted_text
    lower_text = text.lower()

    if DEEPSEEK_SERVER_URL:
        try:
            prompt = f"""You are a child safety guardian AI analyzing text scraped from a child's device.
App: {payload.app_name}
Title: {payload.content_title}
Text to evaluate:
\"\"\"{text}\"\"\"

Analyze if this contains:
1. Stranger Danger / Luring / Secret meeting requests
2. Cyberbullying / Harassment / Hate speech
3. Explicit or Age-Inappropriate material
4. Self-Harm or Depression indicators
5. Academic Cheating / Plagiarism bypassing
6. Physical Violence, Threats, Weapons, or Harm to Others (e.g. searching how to threat someone, kill, weapons, physical attacks, bomb, poison)

Respond ONLY in valid JSON matching this schema:
{{
  "threat_detected": boolean,
  "threat_category": "Violence & Threats" | "Stranger Risk" | "Cyberbullying" | "Explicit Content" | "Self-Harm" | "Academic Distraction" | "Safe",
  "confidence_score": float (0.0 to 1.0),
  "ai_analysis_summary": "Short 1-2 sentence explanation for the parent"
}}"""

            headers = {"Content-Type": "application/json"}
            if DEEPSEEK_API_KEY:
                headers["Authorization"] = f"Bearer {DEEPSEEK_API_KEY}"

            req_body = {
                "model": DEEPSEEK_MODEL,
                "messages": [
                    {"role": "system", "content": "You are a specialized parental control AI safety evaluator."},
                    {"role": "user", "content": prompt}
                ],
                "temperature": 0.1,
                "max_tokens": 250
            }

            status_code = None
            response_json = None

            if httpx is not None:
                async with httpx.AsyncClient(timeout=10.0) as client:
                    resp = await client.post(DEEPSEEK_SERVER_URL, json=req_body, headers=headers)
                    status_code = resp.status_code
                    response_json = resp.json()
            elif requests is not None:
                resp = requests.post(DEEPSEEK_SERVER_URL, json=req_body, headers=headers, timeout=10.0)
                status_code = resp.status_code
                response_json = resp.json()

            if status_code == 200 and response_json:
                content = response_json.get("choices", [{}])[0].get("message", {}).get("content", "")
                start = content.find("{")
                end = content.rfind("}")
                if start != -1 and end != -1:
                    parsed = json.loads(content[start:end+1])
                    if parsed.get("threat_detected"):
                        log = ActivityLogModel(
                            log_id=f"log-{uuid.uuid4().hex[:8]}",
                            child_id=payload.child_id,
                            package_name=payload.package_name,
                            app_name=payload.app_name,
                            content_title=payload.content_title,
                            extracted_text=payload.extracted_text,
                            is_flagged=True,
                            threat_category=parsed.get("threat_category", "Vulnerability Detected"),
                            confidence_score=float(parsed.get("confidence_score", 0.9)),
                            ai_analysis_summary=parsed.get("ai_analysis_summary", ""),
                            recorded_at=int(time.time() * 1000),
                            is_synced=True
                        )
                        db.add(log)
                        db.commit()

                    return ThreatEvaluateResponse(
                        threat_detected=parsed.get("threat_detected", False),
                        threat_category=parsed.get("threat_category", "Safe"),
                        confidence_score=float(parsed.get("confidence_score", 0.0)),
                        ai_analysis_summary=parsed.get("ai_analysis_summary", "Evaluation complete."),
                        model_used="DeepSeek (Dedicated Server)"
                    )
        except Exception as e:
            # Graceful fallback to rule engine if DeepSeek inference server is busy/unreachable
            pass

    # Built-in High-Accuracy Rule Engine (Fallback & Instant Safety Gate)
    threat_detected = False
    threat_category = "Safe"
    confidence = 0.0
    summary = "No vulnerability detected in this content."

    # 1. Physical Violence, Weapons & Threats to Others
    violence_words = [
        "kill", "kills", "killing", "threat", "threats", "threaten", "threatens",
        "threatening", "murder", "murders", "stab", "stabs", "shoot", "shoots",
        "shooting", "gun", "guns", "knife", "knives", "bomb", "bombs", "poison"
    ]
    violence_phrases = [
        "how to threat", "threat someone", "threaten someone", "how to kill",
        "kill someone", "hurt someone", "harm someone", "beat up", "mass shooting",
        "school shooting", "death threat", "bring a gun", "bring a knife", "punch in the face"
    ]

    is_violence = any(re.search(r'\b' + re.escape(w) + r'\b', lower_text) for w in violence_words) or any(p in lower_text for p in violence_phrases)

    if is_violence:
        threat_detected = True
        threat_category = "Violence & Threats"
        confidence = 0.96
        summary = "Search query or message involving physical violence, death threat, weapons, or harm to others detected."
    elif any(k in lower_text for k in ["meet up", "alone right now", "don't tell your mom", "dont tell your parents", "secret meeting", "free robux code"]):
        threat_detected = True
        threat_category = "Stranger Risk"
        confidence = 0.95
        summary = "Potential stranger solicitation or suspicious secrecy request detected."
    elif any(k in lower_text for k in ["kill yourself", "loser", "nobody likes you", "ugly", "freak", "stupid idiot"]):
        threat_detected = True
        threat_category = "Cyberbullying"
        confidence = 0.92
        summary = "Hostile or harassing language targeted at child detected."
    elif any(k in lower_text for k in ["bypass turnitin", "write my essay fast bot", "cheat exam questions"]):
        threat_detected = True
        threat_category = "Academic Distraction"
        confidence = 0.88
        summary = "Attempt to bypass academic integrity tools detected."
    elif any(k in lower_text for k in ["suicide", "want to die", "cut myself", "end my life"]):
        threat_detected = True
        threat_category = "Self-Harm"
        confidence = 0.98
        summary = "Critical distress or self-harm keywords detected. Immediate attention recommended."

    if threat_detected:
        try:
            # Ensure child user exists in Supabase to satisfy Foreign Key
            child_user = db.query(UserModel).filter(UserModel.user_id == payload.child_id).first()
            if not child_user:
                group = db.query(FamilyGroupModel).first()
                if not group:
                    group = FamilyGroupModel(group_id="group-default", family_name="FocusSense Family")
                    db.add(group)
                    db.flush()
                child_user = UserModel(
                    user_id=payload.child_id,
                    group_id=group.group_id,
                    email=f"{payload.child_id}@family.focussense",
                    password_hash="synced_child",
                    role="child",
                    name="Child Device",
                    pin=""
                )
                db.add(child_user)
                db.commit()

            log = ActivityLogModel(
                log_id=f"log-{uuid.uuid4().hex[:8]}",
                child_id=payload.child_id,
                package_name=payload.package_name,
                app_name=payload.app_name,
                content_title=payload.content_title,
                extracted_text=payload.extracted_text,
                is_flagged=True,
                threat_category=threat_category,
                confidence_score=confidence,
                ai_analysis_summary=summary,
                recorded_at=int(time.time() * 1000),
                is_synced=True
            )
            db.add(log)
            db.commit()
        except Exception as dbe:
            db.rollback()
            print("Database log recording skipped:", str(dbe))

    return ThreatEvaluateResponse(
        threat_detected=threat_detected,
        threat_category=threat_category,
        confidence_score=confidence,
        ai_analysis_summary=summary,
        model_used="FocusSense Rule Engine"
    )

# ---------------------------------------------------------------------------
# 4. Activity Logs & Zero Data-Loss Sync (Child -> Server)
# ---------------------------------------------------------------------------
@app.post("/api/sync")
def sync_child_data(payload: SyncPayload, db: Session = Depends(get_db)):
    """Receives offline-queued logs and locations from Child devices."""
    saved_logs_count = 0
    flagged_alerts_count = 0

    # Ensure child user exists in Supabase so ForeignKey doesn't fail
    child_user = db.query(UserModel).filter(UserModel.user_id == payload.child_id).first()
    if not child_user:
        group = db.query(FamilyGroupModel).first()
        if not group:
            group = FamilyGroupModel(group_id="group-default", family_name="FocusSense Family")
            db.add(group)
            db.flush()
        child_user = UserModel(
            user_id=payload.child_id,
            group_id=group.group_id,
            email=f"{payload.child_id}@family.focussense",
            password_hash="synced_child",
            role="child",
            name="Child Device",
            pin=""
        )
        db.add(child_user)
        db.commit()

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

# ---------------------------------------------------------------------------
# 5. Parent Queries: Read & Manage Activity Logs
# ---------------------------------------------------------------------------
@app.get("/api/logs/{child_id}")
def get_child_logs(child_id: str, flagged_only: bool = False, db: Session = Depends(get_db)):
    query = db.query(ActivityLogModel).filter(ActivityLogModel.child_id == child_id)
    if flagged_only:
        query = query.filter(ActivityLogModel.is_flagged == True)
    return query.order_by(ActivityLogModel.recorded_at.desc()).limit(100).all()

@app.delete("/api/logs/{log_id}")
def delete_vulnerable_log(log_id: str, db: Session = Depends(get_db)):
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

# ---------------------------------------------------------------------------
# 6. Schedule Rules (Timetables & Curfews)
# ---------------------------------------------------------------------------
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

# ---------------------------------------------------------------------------
# 7. Live GPS Telemetry
# ---------------------------------------------------------------------------
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
