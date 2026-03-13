# Epic 32: Wear OS Watch Face Management (Single-App Architecture)

> Supersedes: Original Epic 32 (WFF Watch Face & Watch Alert Management)
> Architecture: See `docs/wear-os-architecture.md`
> Created: 2026-03-09

## Context

The user's vision: GlycemicGPT is a single phone app that manages everything, including pushing watch faces to the watch and controlling all watch features. There is NO separate wear companion phone app. The watch face shows live glucose data, trends, IoB, alerts, and a sparkline graph -- all customizable from the phone.

### What exists (from Story 31.7 branch)
- `WearDataSender` in `:app` -- pushes BG, IoB, alerts, CGM history via DataLayer
- `WearChatRelayService` in `:app` -- handles AI chat relay from watch
- Settings > Watch section in `:app` -- basic connection status
- Old `wear/` module -- watch-side app with services, complications, watch UIs (to be migrated)
- `watchface/` module -- WFF v1 XML watch face (working, appears in Samsung picker)
- 6 complication providers (BG, BgColor, IoB, Trend, Alert, Graph)
- `GlycemicDataListenerService` -- receives DataLayer events on watch
- `WatchDataRepository` -- StateFlow-based data store on watch

### Target
- Galaxy Watch 5 updated to Wear OS 6 (API 36)
- Watch Face Push API (`androidx.wear.watchface:watchface-push`)
- Single GlycemicGPT phone app manages everything

---

## Stories

### 32.1: Create wear-device Module & Migrate Watch Services

**Scope:** Create new `:wear-device` Gradle module for watch-side services. Migrate all watch-side code from old `wear/` module. Remove phone-side watch UIs (HomeActivity, ChatActivity, IoBDetailActivity) that don't belong on the watch in the new architecture.

**Files:**
- NEW: `apps/mobile/wear-device/build.gradle.kts` -- `minSdk = 36`, Wear OS 6 target, watchface-push dependency
- NEW: `apps/mobile/wear-device/src/main/AndroidManifest.xml` -- Services, complications, permissions for Watch Face Push
- MIGRATE: `GlycemicDataListenerService` -> `wear-device/`
- MIGRATE: `WatchDataRepository` -> `wear-device/`
- MIGRATE: `GlucoseDisplayUtils` -> `wear-device/`
- MIGRATE: All 6 complication providers -> `wear-device/`
- MIGRATE: `AlertsActivity` -> `wear-device/` (simplified)
- MIGRATE: `GlycemicTileService` -> `wear-device/` (optional)
- MIGRATE: `GlycemicWearApp` (Hilt) -> `wear-device/`
- MODIFY: `settings.gradle.kts` -- Add `:wear-device`, keep `:watchface`
- DO NOT MIGRATE: HomeActivity, ChatActivity, IoBDetailActivity (removed)

**Verify:** `:wear-device:assembleDebug` builds. Installs on watch. DataLayer events received. Complications update.

**Depends on:** Nothing (foundation)

---

### 32.2: Watch Face Push Integration (wear-device side)

**Scope:** Add WatchFacePushManager to `:wear-device`. The watch-side app can receive WFF APK bytes from the phone, install the watch face, and activate it.

**Files:**
- NEW: `wear-device/.../WatchFacePushService.kt` -- Receives APK via ChannelClient, calls `WatchFacePushManager.addWatchFace()`, handles `setWatchFaceAsActive()`
- MODIFY: `wear-device/AndroidManifest.xml` -- Add Push permissions (`PUSH_WATCH_FACES`, `SET_PUSHED_WATCH_FACE_AS_ACTIVE`)
- MODIFY: `wear-device/build.gradle.kts` -- Add `androidx.wear.watchface:watchface-push` dependency

**Verify:** Phone app can send WFF APK bytes via ChannelClient. Watch-side receives, installs face, face appears as active.

**Depends on:** 32.1

---

### 32.3: Phone-Side Watch Face Push (ChannelClient in :app)

**Scope:** Add ChannelClient-based face push to the main app. Bundle the WFF APK in app assets. When user taps "Set Watch Face" in Settings, the app streams the APK to the watch.

**Files:**
- NEW: `app/.../wear/WatchFacePusher.kt` -- Opens ChannelClient to watch node, streams APK bytes from assets
- MODIFY: `app/build.gradle.kts` -- Depend on `:watchface` assets or copy APK to assets
- MODIFY: `app/.../wear/WearDataContract.kt` -- Add face push channel path
- COPY: `watchface-debug.apk` -> `app/src/main/assets/glycemicgpt-watchface.apk` (build task)

**Verify:** Tapping "Set Watch Face" in phone Settings pushes APK to watch. Face installs and activates within 5 seconds.

**Depends on:** 32.2

---

### 32.4: Settings > Watch -- Full Management UI

**Scope:** Build out the phone app's Settings > Watch section as the complete watch management hub. Shows connection status, watch face preview, feature toggles, and "Set Watch Face" button.

**Files:**
- MODIFY: `app/.../presentation/settings/SettingsScreen.kt` -- Full Watch management section
- MODIFY: `app/.../presentation/settings/SettingsViewModel.kt` -- Watch state, feature toggle sync, face push trigger

**UI Layout:**
```
Settings > Watch
+------------------------------------------+
|  Watch Connection                        |
|  [green dot] Galaxy Watch 5 - Connected  |
|  Streaming BG, IoB, alerts, CGM history  |
+------------------------------------------+
|  Watch Face                              |
|  [preview image of current face]         |
|  [Set Watch Face]  [Customize]           |
+------------------------------------------+
|  Watch Face Features                     |
|  [toggle] Show IoB                       |
|  [toggle] Show Glucose Graph             |
|  [toggle] Show Alert Indicator           |
|  [toggle] Show Seconds                   |
|  Graph Range: [1H] [3H] [6H]            |
+------------------------------------------+
|  Theme                                   |
|  ( ) Dark   ( ) Clinical Blue            |
|  ( ) High Contrast                       |
+------------------------------------------+
|  Data                                    |
|  Last BG sent: 234 mg/dL (2m ago)        |
|  Last IoB sent: 5.25u                    |
|  CGM history points: 288                 |
+------------------------------------------+
```

**Verify:** All toggles work. Changes sync to watch via DataLayer. Face preview renders. "Set Watch Face" pushes and activates face.

**Depends on:** 32.3

---

### 32.5: Feature Toggle Sync (Phone -> Watch -> WFF)

**Scope:** When user changes feature toggles in Settings > Watch, sync the config to the watch via DataLayer. The watch-side updates WFF UserConfiguration values so the watch face shows/hides elements in real-time.

**Files:**
- MODIFY: `app/.../wear/WearDataSender.kt` -- Add `sendWatchFaceConfig()` method
- MODIFY: `app/.../wear/WearDataContract.kt` -- Add config data path
- MODIFY: `wear-device/.../GlycemicDataListenerService.kt` -- Handle config changes, update WFF UserConfiguration
- MODIFY: `wear-device/.../WearDataContract.kt` -- Add config data path

**Config payload:** JSON with boolean flags: `show_iob`, `show_graph`, `show_alert`, `show_seconds`, `graph_range`, `theme`

**Verify:** Toggle IoB off in phone Settings -> watch face hides IoB within 2 seconds. Same for all toggles.

**Depends on:** 32.4

---

### 32.6: Watch Face Gallery & Multiple Faces

**Scope:** Add support for multiple WFF watch face designs. Phone Settings shows a gallery of available faces. User can browse previews and push their preferred face.

**Files:**
- NEW: `watchface/src/main/res/raw/watchface_minimal.xml` -- Minimal face (time + BG only)
- NEW: `watchface/src/main/res/raw/watchface_clinical.xml` -- Clinical face (all data, no graph)
- MODIFY: `watchface/src/main/res/raw/watchface.xml` -- Rename to `watchface_full.xml` (full xDrip-style)
- MODIFY: `app/.../wear/WatchFacePusher.kt` -- Support pushing different face variants
- MODIFY: `app/.../presentation/settings/SettingsScreen.kt` -- Gallery UI with previews

**Face variants:**
1. **Full** -- BG hero + trend + delta + freshness + IoB + time + sparkline graph (xDrip-style)
2. **Clinical** -- BG + trend + time + IoB + alert (no graph, high contrast)
3. **Minimal** -- BG + trend + time only (low battery mode)

**Verify:** Gallery shows 3 faces with previews. Tapping one pushes it to watch. Face changes within 5 seconds.

**Depends on:** 32.3

---

### 32.7: Ambient Mode & Battery Optimization

**Scope:** Polish WFF ambient mode for OLED burn-in protection. Hide graph in ambient. Use thin fonts. Ensure proper Variant elements.

**Files:**
- MODIFY: All `watchface*.xml` files -- Refined ambient Variant elements, burn-in protection, minimal pixel coverage

**Verify:** Screen off -> ambient shows time + BG only. No graph, minimal pixels. Smooth transitions.

**Depends on:** 32.6

---

### 32.8: Watch-to-Phone AI Chat & Alert Dismiss

**Scope:** Wire up watch face tap targets for AI queries and alert dismissal. Taps on the watch face send messages to the phone via MessageClient. Phone routes to backend.

**Files:**
- MODIFY: `wear-device/.../AlertsActivity.kt` -- Dismiss button sends message to phone
- MODIFY: `app/.../wear/WearChatRelayService.kt` -- Handle alert dismiss + AI query messages
- MODIFY: `wear-device/.../WearDataContract.kt` -- Add alert dismiss path
- MODIFY: `app/.../wear/WearDataContract.kt` -- Add alert dismiss path

**Verify:** Tap alert zone on face -> AlertsActivity opens -> dismiss -> clears on phone + watch. AI query from watch -> response appears on watch.

**Depends on:** 32.1, 32.2

---

### 32.9: Remove Old wear/ Module & Cleanup

**Scope:** Delete the old `apps/mobile/wear/` module entirely. Remove from settings.gradle.kts. Update build scripts, CI, and all documentation references.

**Files:**
- DELETE: `apps/mobile/wear/` (entire directory)
- MODIFY: `apps/mobile/settings.gradle.kts` -- Remove `:wear` include
- MODIFY: `scripts/mobile-dev.sh` -- Update build commands (`:wear-device` instead of `:wear`)
- MODIFY: `.github/workflows/android.yml` -- Update CI for `:wear-device`
- MODIFY: `CLAUDE.md` -- Update Wear OS dev instructions
- MODIFY: `docs/wear-os-architecture.md` -- Mark migration complete

**Verify:** `./gradlew assembleDebug` builds all modules. No references to old `wear/` module. CI passes.

**Depends on:** 32.1 through 32.8 (all migration complete)

---

### 32.10: Update Documentation & Dev Scripts

**Scope:** Update all documentation to reflect the single-app architecture. Update dev scripts for the new module structure.

**Files:**
- MODIFY: `docs/wear-os-architecture.md` -- Final review
- MODIFY: `docs/plugin-architecture.md` -- Remove wear module references if any
- MODIFY: `scripts/mobile-dev.sh` -- Build/install commands for new modules
- MODIFY: `CLAUDE.md` -- Update Wear OS development workflow
- MODIFY: `_bmad-output/PROGRESS.md` -- Update Epic 32 tracking

**Verify:** All docs accurate. Dev scripts work. New developer can follow docs to set up watch development.

**Depends on:** 32.9

---

### 32.11: Fix Watch Face Push Validation Token

**Scope:** The Watch Face Push API on Wear OS 6 requires a validation token that proves the WFF APK is legitimate. Our current `WatchFaceReceiveService` calls `addWatchFace()` but the token is wrong or missing. Watch logcat shows: `Watch face install failed: The validation token provided does not match the watch face.`

**Root cause investigation:**
- The Watch Face Push API docs require the phone app to provide a validation token when calling `addWatchFace()`
- The token is derived from the WFF APK's signing certificate and must match the certificate used to sign the APK
- Debug vs release signing may produce different tokens
- The `WatchFaceReceiveService` may be passing an empty/stale token

**Files:**
- MODIFY: `wear-device/.../push/WatchFaceReceiveService.kt` -- Fix validation token generation for `addWatchFace()`
- MODIFY: `app/.../wear/WatchFacePusher.kt` -- May need to send signing info alongside APK bytes
- MODIFY: `watchface/build.gradle.kts` -- Ensure consistent signing config
- INVESTIGATE: Whether debug-signed WFF APKs require a different token path than release-signed

**Verify:**
- Phone taps "Set Watch Face" -> watch installs face -> face becomes active with live BG/IoB/trend data
- Phone Settings > Watch shows success status
- Watch face displays real-time glucose data from the DataLayer
- Take screenshot of working watch face on physical Galaxy Watch 5

**Depends on:** 32.3 (existing push mechanism)
**Priority:** HIGH -- blocks visual verification of all watch face features

---

### 32.12: Watch APK Self-Update via Phone App

**Scope:** The watch services APK (`:wear-device`) currently requires ADB sideloading to update. Users cannot be expected to do this. The phone app should manage watch APK updates using the same GitHub Releases pattern as the phone's self-update (`AppUpdateChecker`).

**Approach:**
1. Phone checks GitHub Releases for `GlycemicGPT-Wear-*` APK (same `dev-latest` / `latest` pattern as phone)
2. Downloads newer wear APK to local cache
3. Pushes APK bytes to watch via `ChannelClient` (same transfer mechanism as watch face push)
4. Watch side receives bytes, validates, and triggers `PackageInstaller` session API to self-update

**Key constraint:** Wear OS does NOT support `ACTION_VIEW` with APK files like phones do. Must use `PackageInstaller` session API for silent self-update, which requires the watch app to hold `REQUEST_INSTALL_PACKAGES` permission.

**Files:**
- NEW: `app/.../wear/WearAppUpdater.kt` -- Checks GitHub Releases for newer wear APK, downloads, pushes via ChannelClient
- NEW: `wear-device/.../update/WearSelfUpdateService.kt` -- Receives APK bytes via ChannelClient, validates signature, installs via PackageInstaller session
- MODIFY: `wear-device/AndroidManifest.xml` -- Add ChannelClient listener for update path, REQUEST_INSTALL_PACKAGES permission
- MODIFY: `app/.../wear/WearDataContract.kt` -- Add wear update channel path
- MODIFY: `wear-device/.../data/WearDataContract.kt` -- Add wear update channel path
- MODIFY: `app/.../presentation/settings/SettingsScreen.kt` -- Watch App Version display + "Check for Watch Update" button
- MODIFY: `app/.../presentation/settings/SettingsViewModel.kt` -- Watch update check/download/push state
- MODIFY: `wear-device/build.gradle.kts` -- Add `DEV_BUILD_NUMBER` BuildConfig field (same pattern as phone)
- MODIFY: `.github/workflows/android.yml` -- Ensure wear APK version metadata matches phone pattern

**Verify:**
- Settings > Watch shows current watch app version
- "Check for Watch Update" finds newer version from GitHub Releases
- Download + push to watch succeeds
- Watch app restarts with new version
- Both dev and prod update channels work

**Depends on:** 32.11 (validates ChannelClient push works correctly)
**Priority:** HIGH -- required for non-developer users

---

## Dependency Graph

```
32.1 (wear-device module + migration)
 |
 +-> 32.2 (Watch Face Push - watch side)
 |    |
 |    +-> 32.3 (Watch Face Push - phone side)
 |         |
 |         +-> 32.4 (Settings > Watch UI)
 |         |    |
 |         |    +-> 32.5 (Feature toggle sync)
 |         |
 |         +-> 32.6 (Watch face gallery)
 |         |    |
 |         |    +-> 32.7 (Ambient mode polish)
 |         |
 |         +-> 32.11 (Fix watch face push validation token) ** PRIORITY **
 |              |
 |              +-> 32.12 (Watch APK self-update via phone)
 |
 +-> 32.8 (AI chat & alert dismiss)
 |
 All -> 32.9 (Remove old wear/ module)
         |
         +-> 32.10 (Documentation update)
```

**Recommended order:** 32.1 -> 32.2 -> 32.3 -> 32.4 -> 32.5 -> 32.8 -> 32.9 -> 32.10 -> **32.11** -> **32.12** -> 32.6 -> 32.7

---

## Prerequisites

1. **Galaxy Watch 5 must be updated to Wear OS 6** (API 36) -- Watch Face Push API requires it
2. **Phone must have Google Play Services** with Wearable API support
3. **Watch must be paired** with phone via Bluetooth

## Key Risks

1. **Watch Face Push API is Wear OS 6 only** -- Galaxy Watch 5 supports it but must be updated first
2. **WFF v1 compatibility** -- our WFF XML must stay v1 for broadest support (no Flavors/Weather)
3. **1 slot limit** -- Watch Face Push allows only 1 face per marketplace app. We manage variants by pushing different WFF APKs to the same slot
4. **ChannelClient reliability** -- Bluetooth transfer of APK bytes; need retry logic for interrupted transfers
5. **Complication binding after face push** -- WFF DefaultProviderPolicy must reference correct wear-device package name
