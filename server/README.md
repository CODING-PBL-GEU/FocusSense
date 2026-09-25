# FocusSense Central Server & PostgreSQL (Supabase) Setup Guide

This backend connects parent and child Android devices across the internet according to the FocusSense Architecture.

---

## Architecture Flow

```
[Child Device]   ──(Sync / Location / Flagged Alerts)──> [FastAPI Server] ──> [PostgreSQL / Supabase]
                                                               │
                                                               └──(FCM / REST API)──> [Parent Device]
```

---

## 1. Supabase PostgreSQL Setup (2 Minutes)

1. Create a free project at [Supabase.com](https://supabase.com).
2. Go to the **SQL Editor** tab in your Supabase dashboard.
3. Paste the contents of `schema.sql` and click **Run**.
   - This creates `family_groups`, `users`, `devices`, `activity_logs`, `schedule_rules`, and `location_history`.
4. Copy your Database Connection string from **Project Settings -> Database**:
   ```
   postgresql://postgres:[YOUR-PASSWORD]@db.[YOUR-PROJECT-REF].supabase.co:5432/postgres
   ```

---

## 2. Running the Server

### Option A: Free Cloud Deployment (Render / Railway / GCP Cloud Run / Fly.io)
1. Push this `/server` directory to GitHub.
2. In Render or Railway, choose **New Web Service** from GitHub.
3. Set the environment variable:
   - `DATABASE_URL`: Your Supabase connection string.
4. Build Command: `pip install -r requirements.txt`
5. Start Command: `uvicorn main:app --host 0.0.0.0 --port $PORT`
6. Your live server URL will be: `https://your-app.onrender.com`

### Option B: Run Locally on your computer
```bash
cd server
pip install -r requirements.txt
export DATABASE_URL="postgresql://postgres:password@db.xxxx.supabase.co:5432/postgres"
python main.py
```
*(Server will start on `http://localhost:8000` or `http://0.0.0.0:8000`)*

---

## 3. Connecting Android Devices to the Server

In the Android app:
- Both Parent and Child devices can enter the server URL (e.g. `https://your-app.onrender.com` or `http://10.0.2.2:8000` for Android Emulator).
- When the Child device extracts flagged text or updates GPS, it calls `POST /api/sync`.
- The Parent device immediately polls or receives FCM updates from `GET /api/logs/{child_id}` and `GET /api/location/{child_id}/latest`.
