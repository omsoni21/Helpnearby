# 🧪 Testing & Quality Assurance Guide

> **HelpNearby** features comprehensive unit, Robolectric, Room, security, sync, and Compose UI journey test coverage running locally on the JVM.

---

## 🚀 Running Tests

Run all unit & integration test suites directly from terminal:

```bash
./gradlew :app:testDebugUnitTest
```

---

## 📊 Test Suite Matrix

| Testing Area | Test Class | Key Scenarios Covered |
|:---|:---|:---|
| **Auth & Account Deletion** | `HelpNearbyCoreLogicTest` | Email validation, registration, duplicate rejection, account deletion cascades |
| **Help Requests & Privacy** | `HelpNearbyCoreLogicTest` | Request creation, owner-only edit/cancel checks, public phone leak prevention |
| **Volunteer Flow** | `HelpNearbyCoreLogicTest` | `OPEN ➔ ACCEPTED ➔ COMPLETED` lifecycle, volunteer completion counters |
| **Private Chat & Blocking** | `HelpNearbyCoreLogicTest` | Auto conversation creation, participant-only read/write, user block filtering |
| **Blood Donor Privacy** | `HelpNearbyCoreLogicTest` | Explicit opt-in consent requirement, non-public contact details enforcement |
| **Lost & Found Claims** | `HelpNearbyCoreLogicTest` | Owner verification hint verification, private claim workflow |
| **Offline Room & Sync** | `HelpNearbyCoreLogicTest` | Mutations queue (`PendingSyncOperationEntity`), `shouldLocalWin` conflict resolution |
| **Compose UI Journeys** | `HelpNearbyUiJourneyTest` | Bilingual English/Hindi onboarding toggle, skip flow, India 112 Safety Center |
| **Robolectric Integration** | `ExampleRobolectricTest` | Application context & resource string resolution (`app_name`) |

---

## 📘 Quick Links
- 📘 [Main Readme (`README.md`)](../README.md)
- ⚙️ [Backend Setup Guide (`docs/SETUP_BACKEND.md`)](SETUP_BACKEND.md)
