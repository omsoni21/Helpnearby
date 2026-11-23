# ⚙️ Backend Setup Guide

> **HelpNearby** operates **offline-first** by default using **Room Database** and **WorkManager**. Connecting a live cloud backend (Supabase or Firebase) enables authentication, real-time sync, and multi-device cloud storage.

---

## ⚡ Option A: Supabase Setup (Recommended)

[![Supabase](https://img.shields.io/badge/Backend-Supabase-3ECF8E.svg?style=flat-square&logo=supabase)](https://supabase.com)

### 1. Execute SQL Schema
Create a project on [Supabase](https://supabase.com). Open **SQL Editor**, paste and run [`docs/SUPABASE_SCHEMA.sql`](SUPABASE_SCHEMA.sql). This initializes all 17 database tables, indexes, RLS policies, and Realtime triggers.

### 2. Configure Environment Variables (`.env`)
Add your project credentials to `.env` in the project root:

```env
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=YOUR_SUPABASE_ANON_KEY
GOOGLE_WEB_CLIENT_ID=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

### 3. Configure Google Sign-In & Redirects
- **Supabase Dashboard** ➔ **Authentication** ➔ **Providers** ➔ **Google**: Enable and provide your Google OAuth Client ID & Secret.
- **URL Configuration** ➔ **Redirect URLs**: Add `helpnearby://auth-callback`.

### 4. Assign Admin Role
Assign `raw_app_meta_data.role = 'admin'` via Supabase Service Role for elevated moderation privileges.

---

## ⚡ Option B: Firebase Setup (Alternative)

[![Firebase](https://img.shields.io/badge/Backend-Firebase-FFCA28.svg?style=flat-square&logo=firebase)](https://firebase.google.com)

### 1. Add Configuration
Create a project in [Firebase Console](https://console.firebase.google.com), register package `com.helpnearby.app`, and place `google-services.json` inside the `app/` directory.

### 2. Deploy Firestore Rules
Enable **Firebase Auth** and **Cloud Firestore**, then deploy rules:

```bash
firebase deploy --only firestore:rules
```

### 3. Assign Admin Custom Claims
Set custom claims via Firebase Admin SDK: `admin.auth().setCustomUserClaims(uid, { admin: true })`.

---

## 📘 Quick Links
- 🗄️ [Supabase SQL Schema (`docs/SUPABASE_SCHEMA.sql`)](SUPABASE_SCHEMA.sql)
- 🔒 [Firestore Rules & Schema (`docs/FIRESTORE_RULES_AND_SCHEMA.md`)](FIRESTORE_RULES_AND_SCHEMA.md)
- 🧪 [Testing Guide (`docs/TESTING.md`)](TESTING.md)
