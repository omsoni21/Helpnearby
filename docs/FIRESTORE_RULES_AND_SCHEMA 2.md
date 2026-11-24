# 🔒 Firestore Schema & Security Rules

> Reference documentation for running **HelpNearby** on **Firebase Cloud Firestore** (Free Spark Plan).

---

## 🗄️ Database Collections

| Collection Path | Purpose | Key Attributes |
|:---|:---|:---|
| `users/{userId}` | User accounts & roles | `id, fullName, email, city, state, role ('MEMBER'/'ADMIN'), isSuspended` |
| `help_requests/{requestId}` | Community help requests | `id, ownerId, title, category, city, urgency, status ('OPEN'..'COMPLETED')` |
| `volunteer_profiles/{userId}` | Volunteer directory | `userId, bio, skills[], availability ('AVAILABLE'/'BUSY'), verificationStatus` |
| `donor_profiles/{userId}` | Blood donor registry | `userId, bloodGroup, city, available, consent` *(Contact info private)* |
| `donor_contact_requests/{reqId}`| Private donor requests | `id, donorId, requesterId, message, status ('PENDING'/'ACCEPTED')` |
| `lost_found_posts/{postId}` | Lost & Found items | `id, ownerId, type ('LOST'/'FOUND'), title, category, city, status` |
| `community_posts/{postId}` | Local community posts | `id, authorId, title, content, category, city, status` |
| `conversations/{convId}` | 1-to-1 Request chats | `id, requestId, participantIds[ownerId, volunteerId], status` |
| `notifications/{notifId}` | In-app alerts | `id, userId, type, title, body, readAt` |
| `user_reports/{reportId}` | Moderation reports | `id, reporterId, targetType, targetId, reason, status` |
| `blocked_users/{blockId}` | User block list | `id, blockerId, blockedUserId` |
| `admin_audit_logs/{logId}` | Admin action log | `id, adminId, action, targetId` |

---

## 🛡️ Security Architecture Highlights

1. **Owner-Only Mutations**: Users can only edit or delete their own posts, requests, and profiles.
2. **Participant-Only Chat**: Conversations and messages are restricted to verified request participants.
3. **Blood Donor Privacy**: Phone numbers and exact physical addresses are never stored in public documents.
4. **Zero-Trust Moderation**: Admin endpoints require verified `request.auth.token.admin == true` claims.

---

## 📘 Quick Links
- ⚙️ [Backend Setup Guide (`docs/SETUP_BACKEND.md`)](SETUP_BACKEND.md)
- 🗄️ [Supabase SQL Schema (`docs/SUPABASE_SCHEMA.sql`)](SUPABASE_SCHEMA.sql)
