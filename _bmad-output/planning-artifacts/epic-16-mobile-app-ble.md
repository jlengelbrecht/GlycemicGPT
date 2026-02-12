---
status: planning
startDate: 2026-02-12
totalEpics: 1
totalStories: 12
inputDocuments:
  - prd.md
  - architecture.md
  - PROGRESS.md
context: Android mobile app with BLE pump connectivity for real-time data, faster cloud uploads, watch face, TTS AI chat, and caregiver alerts
---

# Epic 16: GlycemicGPT Android Mobile App with BLE Pump Connectivity

## Overview

The web dashboard depends on Tandem's cloud API for pump data, which introduces a 30-60+ minute delay for IoB and other pump metrics. This delay makes real-time insulin-on-board tracking unreliable.

This epic builds an Android mobile app that connects directly to the Tandem t:slim X2 pump via Bluetooth Low Energy (BLE) using our own implementation of the Tandem BLE protocol, informed by the reverse-engineering work in jwoglom/pumpX2 (MIT licensed). We do NOT depend on pumpX2 as a runtime library -- we study the protocol, rewrite it in Kotlin, and own the code entirely. The app is **read-only** -- it reads IoB, basal rates, bolus history, pump settings, and CGM data from the pump but **never delivers insulin or modifies pump settings**. Bolus delivery may be considered in a future epic after extensive safety review.

The app also uploads pump data to Tandem's cloud at 5-15 minute intervals (vs the official app's ~60 minutes), keeping the user's endocrinologist portal up to date. Simultaneously, it pushes real-time data to the self-hosted GlycemicGPT backend for AI analysis, alerting, and dashboard updates.

Future stories will add a Wear OS watch face, TTS/STT voice AI interface, and native caregiver alert delivery.

## Key Constraints

- **READ-ONLY BLE access only** -- no insulin delivery, no pump setting changes, no control opcodes
- **AI is architecturally firewalled** from any future pump control -- AI modules cannot import or invoke pump command interfaces
- **Single BLE connection** -- the Tandem pump only allows one bonded BLE device at a time. When our app is connected, the official t:connect app cannot be. Users switch between them as needed
- **Reverse-engineered protocol** -- our BLE implementation is informed by jwoglom/pumpX2 (MIT). We own the code, no runtime dependency. Tandem firmware updates could break compatibility and we maintain fixes ourselves
- **Sideloaded APK** -- not distributed through Google Play Store. Users install directly
- **Android-first** -- targeting Android (Kotlin/Jetpack Compose). iOS is out of scope for this epic

## Dependencies

- **GlycemicGPT API** -- existing backend (Epic 1-15), exposed via k8s ingress with auth
- **Tandem cloud API** -- existing tconnectsync-compatible upload endpoints

## Reference Material (NOT runtime dependencies)

- **pumpX2** (github.com/jwoglom/pumpX2, MIT license) -- Java BLE protocol reference. We study this to understand the Tandem BLE protocol (pairing, message framing, request/response types) and rewrite the relevant read-only portions in Kotlin. No runtime dependency.
- **controlX2** (github.com/jwoglom/controlX2, MIT license) -- Reference Android app. We study its BLE service architecture and pairing flow patterns. No code imported directly.
- A `THIRD_PARTY_LICENSES.md` file in the mobile app credits jwoglom/pumpX2 and jwoglom/controlX2 per MIT license requirements.

## Architecture

```
[Tandem t:slim X2 Pump]
        |
        | BLE (read-only status requests)
        |
[GlycemicGPT Android App]
        |
        |--- Push to Tandem Cloud (5-15 min intervals)
        |--- Real-time sync to GlycemicGPT API (HTTPS, cert pinning)
        |--- Watch Face companion (Wear OS) [Story 16.9]
        |--- TTS/STT voice AI chat [Story 16.10]
        |--- Caregiver push notifications [Story 16.11]
        |
[K8s: GlycemicGPT Backend]
        |
        |--- API server (FastAPI)
        |--- AI Sidecar
        |--- PostgreSQL + Redis
```

## Pump Driver Abstraction

The BLE layer is abstracted behind a `PumpDriver` interface to support future pump types:

```kotlin
interface PumpDriver {
    suspend fun connect(deviceAddress: String): ConnectionResult
    suspend fun disconnect()
    suspend fun getIoB(): IoBReading
    suspend fun getBasalRate(): BasalReading
    suspend fun getBolusHistory(since: Instant): List<BolusEvent>
    suspend fun getPumpSettings(): PumpSettings
    suspend fun getBatteryStatus(): BatteryStatus
    suspend fun getReservoirLevel(): ReservoirReading
    fun observeConnectionState(): Flow<ConnectionState>
}

class TandemBleDriver(private val bleAdapter: BluetoothAdapter) : PumpDriver { ... }
// Future: class OmnipodDriver : PumpDriver { ... }
```

---

## Story 16.1: Android Project Scaffolding & Build Configuration

### Story

**As a** developer,
**I want** a properly structured Android project with build tooling and CI,
**So that** we have a solid foundation for the mobile app.

**Epic:** 16 - GlycemicGPT Android Mobile App
**FRs Covered:** Foundation

### Acceptance Criteria

- [ ] **AC1:** Android project created in `apps/mobile/` using Kotlin, Jetpack Compose, Material 3
- [ ] **AC2:** Gradle build configured with minSdk 30 (Android 11), targetSdk 35
- [ ] **AC3:** No external BLE library dependencies -- BLE protocol will be implemented in-house (Story 16.2/16.3)
- [ ] **AC4:** Project structure follows Clean Architecture (data/domain/presentation/ble layers)
- [ ] **AC4a:** `THIRD_PARTY_LICENSES.md` created crediting jwoglom/pumpX2 (MIT) as protocol reference
- [ ] **AC5:** Debug and release build variants configured, with release using ProGuard/R8
- [ ] **AC6:** GitHub Actions workflow builds the APK on push to mobile app branches
- [ ] **AC7:** `.gitignore` properly excludes build artifacts, local.properties, signing keys
- [ ] **AC8:** README in `apps/mobile/` documents build prerequisites and sideloading instructions

### Tasks/Subtasks

- [ ] Create `apps/mobile/` directory with Android project (Android Studio or manual gradle init)
- [ ] Configure `build.gradle.kts` with Kotlin 2.2+, Compose compiler, Material 3, Hilt for DI
- [ ] Set up package structure: `com.glycemicgpt.mobile.{data,domain,presentation,service,ble}`
- [ ] Create `ble/` package for our own Tandem BLE protocol implementation (populated in Story 16.2/16.3)
- [ ] Create debug/release build flavors with signing config placeholder
- [ ] Add GitHub Actions workflow for Android build (setup-java, gradle build)
- [ ] Add README with build instructions, prerequisites (Android SDK, JDK 17)
- [ ] Add `THIRD_PARTY_LICENSES.md` crediting jwoglom/pumpX2 and controlX2 (MIT)

### Dev Notes

- Use Hilt for dependency injection (standard for modern Android)
- Compose BOM for consistent Compose library versions
- minSdk 30 for modern BLE APIs (BLUETOOTH_CONNECT permission model)
- No pumpX2 library dependency -- we implement the BLE protocol ourselves in the `ble/` package

---

## Story 16.2: BLE Connection Manager & Pump Pairing

### Story

**As a** user,
**I want** to pair my Tandem t:slim X2 pump with the app via Bluetooth,
**So that** the app can read data from my pump.

**Epic:** 16 - GlycemicGPT Android Mobile App
**FRs Covered:** Pump connectivity

### Acceptance Criteria

- [ ] **AC1:** App requests and handles Bluetooth and location permissions (required for BLE on Android)
- [ ] **AC2:** BLE scan discovers nearby Tandem pumps and displays them in a list with serial number
- [ ] **AC3:** User selects a pump to pair, and the app completes the Tandem authentication handshake (our implementation)
- [ ] **AC4:** Pairing credentials are stored securely (EncryptedSharedPreferences)
- [ ] **AC5:** App automatically reconnects to the paired pump on launch
- [ ] **AC6:** Connection state is observable (connected/disconnected/connecting) and shown in the UI
- [ ] **AC7:** Graceful handling when pump is out of range or BLE is disabled
- [ ] **AC8:** User can unpair and pair a different pump from settings

### Tasks/Subtasks

- [ ] Implement BLE permission request flow (BLUETOOTH_CONNECT, BLUETOOTH_SCAN, ACCESS_FINE_LOCATION)
- [ ] Build BLE scan screen using Android BLE APIs to discover Tandem pumps by service UUID
- [ ] Implement pairing flow: Tandem authentication challenge-response (based on pumpX2 protocol research)
- [ ] Store pairing credentials in EncryptedSharedPreferences
- [ ] Build `BleConnectionManager` as an Android Service for background connectivity
- [ ] Implement auto-reconnect logic with exponential backoff
- [ ] Create connection state Flow for UI observation
- [ ] Build pump selection UI (list of discovered pumps, pairing progress indicator)
- [ ] Build unpair flow (clear credentials, disconnect)

### Dev Notes

- Tandem pairing requires the user to confirm on the pump's physical screen (security feature)
- The pump only allows ONE bonded device -- pairing our app will unpair t:connect
- BLE connection should run as a foreground service to persist across app lifecycle
- Study controlX2's `PumpService.kt` and pumpX2's auth code to understand the pairing handshake, then implement our own version in Kotlin

---

## Story 16.3: PumpDriver Interface & Tandem BLE Implementation

### Story

**As a** developer,
**I want** a clean abstraction over pump BLE communication,
**So that** the app can support multiple pump types in the future.

**Epic:** 16 - GlycemicGPT Android Mobile App
**FRs Covered:** Multi-pump architecture

### Acceptance Criteria

- [ ] **AC1:** `PumpDriver` interface defined with read-only methods (getIoB, getBasalRate, getBolusHistory, getPumpSettings, getBatteryStatus, getReservoirLevel)
- [ ] **AC2:** `PumpDriver` interface has NO methods for insulin delivery or pump setting changes
- [ ] **AC3:** `TandemBleDriver` implements `PumpDriver` using our own Kotlin BLE protocol implementation (read-only status requests only)
- [ ] **AC4:** IoB retrieved via `ControlIQIOBRequest` (opcode 108)
- [ ] **AC5:** Basal rate retrieved via appropriate pumpX2 status request
- [ ] **AC6:** Bolus history retrieved for a configurable time window
- [ ] **AC7:** All BLE reads have timeouts and return Result types (not exceptions)
- [ ] **AC8:** Unit tests cover the driver with mocked pumpX2 responses

### Tasks/Subtasks

- [ ] Define `PumpDriver` interface in `domain/pump/` package
- [ ] Define data classes: `IoBReading`, `BasalReading`, `BolusEvent`, `PumpSettings`, `BatteryStatus`, `ReservoirReading`
- [ ] Implement `TandemBleDriver` using our own BLE message classes in `ble/messages/`
- [ ] Implement read-only status request messages (Kotlin data classes with serialization):
  - `ControlIQIoBRequest` (opcode 108) -> getIoB()
  - `CurrentBasalStatusRequest` -> getBasalRate()
  - `BolusCalcDataSnapshotRequest` -> getPumpSettings()
  - `InsulinStatusRequest` -> getReservoirLevel()
  - `CurrentBatteryRequest` -> getBatteryStatus()
- [ ] Implement BLE message framing: encode requests, decode responses, handle checksums
- [ ] Add configurable read timeout (default 5s per request)
- [ ] Write unit tests with mocked BLE GATT responses

### Dev Notes

- ONLY implement read-only status request opcodes. Control request opcodes are NEVER implemented in our codebase.
- Reference pumpX2's `com.jwoglom.pumpx2.pump.messages.request.currentStatus` for the 59 read-only opcodes
- pumpX2's `com.jwoglom.pumpx2.pump.messages.request.control` contains 37 control opcodes -- study to understand what to AVOID, never implement
- Our `ble/messages/` package only contains status request types. No control message classes exist in our code.
- Message framing, checksums, and encryption logic should be studied from pumpX2's `PumpCommunicationHandler` and rewritten in Kotlin

---

## Story 16.4: Real-Time Data Polling & Local Storage

### Story

**As a** user,
**I want** the app to continuously read data from my pump in the background,
**So that** I always have up-to-date IoB and pump status.

**Epic:** 16 - GlycemicGPT Android Mobile App
**FRs Covered:** Real-time data

### Acceptance Criteria

- [ ] **AC1:** Background service polls pump for IoB, basal rate, and BG every 30 seconds when connected
- [ ] **AC2:** Bolus history is polled every 5 minutes
- [ ] **AC3:** Battery and reservoir status polled every 15 minutes
- [ ] **AC4:** All readings are stored in local Room database with timestamps
- [ ] **AC5:** Local database retains last 7 days of data (configurable)
- [ ] **AC6:** Polling continues when app is in background (foreground service with notification)
- [ ] **AC7:** Polling pauses gracefully when BLE connection is lost and resumes on reconnect
- [ ] **AC8:** Battery-efficient: reduces poll frequency when phone battery is low (<15%)

### Tasks/Subtasks

- [ ] Create Room database with entities: `IoBReading`, `BasalReading`, `BolusEvent`, `BatteryReading`, `ReservoirReading`
- [ ] Create `PumpDataRepository` with Room DAOs for insert/query/cleanup
- [ ] Build `PumpPollingService` as a foreground service with configurable intervals
- [ ] Implement data freshness tracking (last successful read timestamp per data type)
- [ ] Add battery-aware polling (reduce frequency when phone battery low)
- [ ] Add data retention cleanup (delete readings older than 7 days)
- [ ] Write tests for polling intervals, reconnect behavior, data retention

### Dev Notes

- Use WorkManager for periodic cleanup tasks
- Foreground service notification: "GlycemicGPT - Connected to pump" with IoB display
- Room provides reactive Flows for UI observation

---

## Story 16.5: Backend Sync -- Push Real-Time Data to GlycemicGPT API

### Story

**As a** user,
**I want** my pump data to be pushed to my GlycemicGPT backend in real-time,
**So that** the web dashboard, AI analysis, and alerts use current data.

**Epic:** 16 - GlycemicGPT Android Mobile App
**FRs Covered:** Backend integration

### Acceptance Criteria

- [ ] **AC1:** App authenticates with GlycemicGPT API using the same login credentials (email/password -> JWT cookie)
- [ ] **AC2:** New IoB readings are pushed to the API within 5 seconds of being read from the pump
- [ ] **AC3:** Bolus events are pushed as they are detected
- [ ] **AC4:** Push uses a queue with retry logic -- data is not lost if the network is temporarily unavailable
- [ ] **AC5:** API endpoint accepts pump data pushes (new endpoint or extension of existing Tandem sync)
- [ ] **AC6:** Backend API validates the push and stores events in `pump_events` table using existing schema
- [ ] **AC7:** HTTPS with certificate pinning to prevent MITM attacks
- [ ] **AC8:** Sync status visible in app (last sync time, pending queue size, errors)

### Tasks/Subtasks

- [ ] Create `BackendSyncService` that observes new local readings and queues API pushes
- [ ] Implement auth flow: login with existing GlycemicGPT credentials, store JWT securely
- [ ] Create new API endpoint: `POST /api/integrations/pump/push` that accepts pump readings from the mobile app
- [ ] Implement offline queue using Room (persist unsent readings, retry on network restore)
- [ ] Add certificate pinning for the GlycemicGPT API domain
- [ ] Add sync status tracking (last success, pending count, last error)
- [ ] Backend: validate pushed data matches expected schema, prevent duplicate events
- [ ] Write API tests for the push endpoint, including auth and validation

### Dev Notes

- The push endpoint is separate from the Tandem cloud sync -- it accepts data directly from the BLE app
- Use OkHttp interceptor for certificate pinning
- Offline queue ensures no data loss during network transitions
- Consider WebSocket or SSE for bidirectional real-time sync in a future story

---

## Story 16.6: Tandem Cloud Upload at Faster Intervals

### Story

**As a** user,
**I want** my pump data uploaded to Tandem's cloud every 5-15 minutes,
**So that** my endocrinologist's Tandem portal stays up to date without depending on the official app.

**Epic:** 16 - GlycemicGPT Android Mobile App
**FRs Covered:** Tandem cloud compatibility

### Acceptance Criteria

- [ ] **AC1:** App uploads pump data to Tandem's cloud API at a configurable interval (default 15 minutes)
- [ ] **AC2:** Upload uses Tandem's existing API format (compatible with t:connect web portal)
- [ ] **AC3:** User provides Tandem credentials in app settings (stored encrypted)
- [ ] **AC4:** Upload interval is configurable: 5, 10, or 15 minutes
- [ ] **AC5:** Upload status shown in settings (last upload time, next scheduled, errors)
- [ ] **AC6:** Uploads continue in background via foreground service
- [ ] **AC7:** If Tandem cloud is unreachable, data is queued and retried

### Tasks/Subtasks

- [ ] Research Tandem's upload API format from tconnectsync source code
- [ ] Implement `TandemCloudUploader` service that batches and uploads pump readings
- [ ] Add Tandem credential entry in app settings with encrypted storage
- [ ] Build configurable upload scheduler (5/10/15 min options)
- [ ] Implement upload retry queue for network failures
- [ ] Add upload status UI in settings
- [ ] Test upload format against Tandem's API (may need to reverse-engineer from tconnectsync)

### Dev Notes

- This is the feature that replaces tconnectpatcher's functionality but built properly
- Tandem's upload API format may need reverse engineering from tconnectsync's upload code
- This is lower priority than stories 16.1-16.5 -- the GlycemicGPT backend sync is more important
- If Tandem's upload API is too difficult to reverse-engineer, this story can be deferred

---

## Story 16.7: App Home Screen & Pump Status Dashboard

### Story

**As a** user,
**I want** to see my pump status at a glance when I open the app,
**So that** I can quickly check IoB, basal rate, battery, and reservoir.

**Epic:** 16 - GlycemicGPT Android Mobile App
**FRs Covered:** Mobile UI

### Acceptance Criteria

- [ ] **AC1:** Home screen displays current IoB prominently (large text, updated in real-time)
- [ ] **AC2:** Current basal rate displayed with Control-IQ mode indicator (Standard/Sleep/Exercise)
- [ ] **AC3:** Battery percentage and reservoir units remaining shown
- [ ] **AC4:** Last BG reading from pump displayed with timestamp
- [ ] **AC5:** Connection status indicator (green=connected, yellow=connecting, red=disconnected)
- [ ] **AC6:** Data freshness indicators (time since last reading for each data type)
- [ ] **AC7:** Pull-to-refresh triggers immediate pump data read
- [ ] **AC8:** Dark theme matching the web app's design language (slate/blue palette)
- [ ] **AC9:** Bottom navigation: Home, AI Chat, Alerts, Settings

### Tasks/Subtasks

- [ ] Design home screen layout in Jetpack Compose with Material 3
- [ ] Create IoB card component (large IoB value, projected decay, last updated)
- [ ] Create basal rate card (rate, mode, automated adjustment indicator)
- [ ] Create pump status bar (battery icon + %, reservoir icon + units)
- [ ] Create BG display card (value, trend arrow if available, timestamp)
- [ ] Add connection status indicator in app bar
- [ ] Implement pull-to-refresh with immediate BLE read
- [ ] Set up bottom navigation scaffold (Home, AI Chat, Alerts, Settings)
- [ ] Apply dark theme consistent with web app

### Dev Notes

- Observe Room database Flows for real-time UI updates
- Use Compose state hoisting pattern with ViewModels
- Match web app's color palette: slate-950 bg, slate-900 cards, blue-600 accents

---

## Story 16.8: App Settings & Configuration

### Story

**As a** user,
**I want** to configure app settings including backend URL, pump pairing, and sync intervals,
**So that** the app connects to my self-hosted instance and works how I prefer.

**Epic:** 16 - GlycemicGPT Android Mobile App
**FRs Covered:** Configuration

### Acceptance Criteria

- [ ] **AC1:** Settings screen with sections: Account, Pump, Sync, About
- [ ] **AC2:** Account: GlycemicGPT backend URL and login credentials
- [ ] **AC3:** Pump: paired pump info, unpair button, re-pair flow
- [ ] **AC4:** Sync: backend sync toggle, Tandem cloud upload toggle and interval (5/10/15 min)
- [ ] **AC5:** Sync: data retention period (1-30 days local storage)
- [ ] **AC6:** All sensitive data (credentials, tokens) stored in EncryptedSharedPreferences
- [ ] **AC7:** Backend URL validation (HTTPS required, connection test button)
- [ ] **AC8:** About: app version, pump firmware version, BLE protocol version, third-party licenses

### Tasks/Subtasks

- [ ] Build settings screen with Compose preference-style layout
- [ ] Implement backend URL configuration with HTTPS validation
- [ ] Implement login/credential management for GlycemicGPT API
- [ ] Add pump management section (paired device info, unpair action)
- [ ] Add sync configuration (toggles, interval picker)
- [ ] Add local data retention configuration
- [ ] Store all settings in EncryptedSharedPreferences via DataStore
- [ ] Add connection test for backend URL (hit `/health` endpoint)

### Dev Notes

- Use Jetpack DataStore (encrypted) for settings persistence
- Backend URL must be HTTPS -- reject HTTP
- Connection test: `GET {backendUrl}/health` with timeout

---

## Story 16.9: Wear OS Watch Face Companion

### Story

**As a** user,
**I want** a watch face on my Wear OS device that shows IoB and BG,
**So that** I can check my pump status with a glance at my wrist.

**Epic:** 16 - GlycemicGPT Android Mobile App
**FRs Covered:** Wearable companion

### Acceptance Criteria

- [ ] **AC1:** Wear OS app module added to the project (`apps/mobile/wear/`)
- [ ] **AC2:** Watch face displays current IoB (large, centered)
- [ ] **AC3:** Watch face displays last BG reading with trend arrow
- [ ] **AC4:** Watch face displays time (standard watch function)
- [ ] **AC5:** Data synced from phone app via Wearable Data Layer API (not direct BLE from watch)
- [ ] **AC6:** Complication provider for IoB and BG (usable on other watch faces)
- [ ] **AC7:** Watch face updates at least every 30 seconds when active, every 5 minutes in ambient
- [ ] **AC8:** Tap on IoB opens a detail view with projected decay (+30min, +60min)
- [ ] **AC9:** Color-coded BG value (green=in range, yellow=high/low, red=urgent)

### Tasks/Subtasks

- [ ] Create Wear OS module in the project
- [ ] Implement Wearable Data Layer sync from phone app to watch
- [ ] Design watch face with Compose for Wear OS
- [ ] Create IoB complication provider
- [ ] Create BG complication provider
- [ ] Implement ambient mode with reduced update frequency
- [ ] Add tap interaction for IoB detail view
- [ ] Add BG color coding based on glucose ranges from settings

### Dev Notes

- Watch communicates with phone app, NOT directly with pump (phone is the BLE bridge)
- Use Wearable Data Layer API for reliable phone-to-watch sync
- Complications allow IoB/BG data on any watch face (not just ours)
- Keep watch face rendering efficient for battery life

---

## Story 16.10: TTS/STT Voice AI Chat

### Story

**As a** user,
**I want** to ask the AI questions by voice and hear responses spoken back,
**So that** I can interact with GlycemicGPT hands-free.

**Epic:** 16 - GlycemicGPT Android Mobile App
**FRs Covered:** Voice AI interface

### Acceptance Criteria

- [ ] **AC1:** AI Chat screen with text input and a microphone button for voice input
- [ ] **AC2:** Tapping the mic button starts speech-to-text recognition
- [ ] **AC3:** Recognized text is displayed and sent to the GlycemicGPT AI chat API
- [ ] **AC4:** AI responses are displayed as text AND spoken aloud via text-to-speech
- [ ] **AC5:** TTS can be toggled on/off in settings
- [ ] **AC6:** Chat history persists locally (last 50 messages)
- [ ] **AC7:** Voice input works while the screen is on (no background voice listening)
- [ ] **AC8:** AI responses include current pump context (IoB, BG, recent boluses) automatically
- [ ] **AC9:** Clear visual feedback during listening (animated mic icon) and speaking (speaker icon)

### Tasks/Subtasks

- [ ] Build AI Chat screen with Compose (message list, text input, send button, mic button)
- [ ] Integrate Android SpeechRecognizer for STT
- [ ] Integrate Android TextToSpeech for TTS
- [ ] Connect to GlycemicGPT AI chat API (`POST /api/ai/chat`)
- [ ] Automatically include current pump context in AI requests (IoB, BG, recent events)
- [ ] Store chat history in Room database
- [ ] Add TTS toggle in settings
- [ ] Add listening/speaking visual indicators
- [ ] Handle STT errors (no speech detected, no internet for cloud STT)

### Dev Notes

- Android's built-in SpeechRecognizer handles STT (uses Google's cloud service)
- Android's TextToSpeech engine handles TTS (local, no internet needed)
- The AI chat API already exists (`POST /api/ai/chat`) -- reuse it
- Include pump context automatically so the user doesn't have to describe their current state

---

## Story 16.11: Caregiver & Emergency Contact Push Notifications

### Story

**As a** caregiver or emergency contact,
**I want** to receive push notifications on my phone when an alert is triggered,
**So that** I am immediately aware of urgent situations.

**Epic:** 16 - GlycemicGPT Android Mobile App
**FRs Covered:** Caregiver alerts

### Acceptance Criteria

- [ ] **AC1:** App can register for push notifications via Firebase Cloud Messaging (FCM)
- [ ] **AC2:** FCM token is sent to the GlycemicGPT backend and associated with the user
- [ ] **AC3:** When an alert is triggered, the backend sends push notifications to all registered caregiver devices
- [ ] **AC4:** Urgent alerts (severe low, severe high) trigger a high-priority notification that overrides Do Not Disturb
- [ ] **AC5:** Non-urgent alerts use standard notification priority
- [ ] **AC6:** Notification includes: alert type, BG value, IoB, patient name
- [ ] **AC7:** Tapping a notification opens the app to the patient's status view
- [ ] **AC8:** Caregivers can install a lite version of the app (no pump pairing needed, just notifications + dashboard view)

### Tasks/Subtasks

- [ ] Set up Firebase project and add FCM to the Android app
- [ ] Implement FCM token registration and send to backend on login
- [ ] Backend: add `device_tokens` table and `POST /api/notifications/register` endpoint
- [ ] Backend: integrate FCM sending into the existing alert escalation pipeline
- [ ] Create notification channels: urgent (override DND), standard, informational
- [ ] Build notification tap handler to deep-link into patient status
- [ ] Design caregiver "lite" build variant (no BLE, no pump pairing, notification + view only)
- [ ] Test notification delivery for various alert types

### Dev Notes

- FCM is free and works on all Android devices with Google Play Services
- For devices without Play Services (e.g., Huawei), consider UnifiedPush as an alternative in a future story
- The existing Telegram alert pipeline (Epic 7) can be extended to also send FCM pushes
- Urgent notifications use `NotificationCompat.PRIORITY_MAX` with `CATEGORY_ALARM`

---

## Story 16.12: Security Hardening & Backend Exposure

### Story

**As a** user,
**I want** the connection between my mobile app and self-hosted backend to be secure,
**So that** my health data is protected when accessed over the internet.

**Epic:** 16 - GlycemicGPT Android Mobile App
**FRs Covered:** Security

### Acceptance Criteria

- [ ] **AC1:** All API communication uses HTTPS with TLS 1.3
- [ ] **AC2:** Certificate pinning configured for the GlycemicGPT API domain
- [ ] **AC3:** JWT tokens stored in EncryptedSharedPreferences (not plain SharedPreferences)
- [ ] **AC4:** App implements token refresh flow (auto-refresh before expiry)
- [ ] **AC5:** Backend rate limiting on mobile API endpoints (prevent abuse)
- [ ] **AC6:** API audit logging for all mobile app requests (IP, user agent, endpoint, timestamp)
- [ ] **AC7:** Network security config blocks cleartext traffic and restricts trusted CAs
- [ ] **AC8:** ProGuard/R8 obfuscation enabled for release builds
- [ ] **AC9:** No sensitive data in Android logs (IoB, BG values, credentials) in release builds
- [ ] **AC10:** Documentation: k8s ingress configuration for secure external exposure

### Tasks/Subtasks

- [ ] Configure OkHttp certificate pinning with backup pins
- [ ] Implement token refresh interceptor in OkHttp
- [ ] Add `network_security_config.xml` blocking cleartext, pinning custom CA if self-signed
- [ ] Backend: add rate limiting middleware for `/api/integrations/pump/push` and mobile endpoints
- [ ] Backend: add audit logging middleware for mobile API requests
- [ ] Configure ProGuard rules for release builds (keep BLE protocol classes, obfuscate app code)
- [ ] Add Timber with release tree that strips sensitive log content
- [ ] Write k8s ingress documentation (cert-manager, Let's Encrypt, ingress-nginx)
- [ ] Security checklist: OWASP Mobile Top 10 review
- [ ] Penetration testing plan for external API exposure

### Dev Notes

- Certificate pinning should include backup pins to avoid lockout during cert rotation
- Consider mTLS (mutual TLS) as an additional layer -- client cert on the phone validates the device
- Rate limiting: 60 req/min for data push, 10 req/min for auth endpoints
- The k8s exposure should use a subdomain with cert-manager + Let's Encrypt for automated TLS

---

## Story Dependency Graph

```
16.1 (Scaffolding)
  |
  v
16.2 (BLE Pairing) --> 16.3 (PumpDriver) --> 16.4 (Polling & Storage)
                                                  |
                                          --------+---------
                                          |                 |
                                          v                 v
                                   16.5 (Backend Sync)  16.6 (Tandem Upload)
                                          |
                                          v
                                   16.7 (Home Screen)
                                          |
                              +-----------+-----------+
                              |           |           |
                              v           v           v
                        16.8 (Settings) 16.9 (Watch) 16.10 (TTS/AI)
                              |
                              v
                        16.11 (Caregiver Push)
                              |
                              v
                        16.12 (Security)
```

Stories 16.1-16.5 are the critical path. Stories 16.6-16.12 can be parallelized after 16.5.
