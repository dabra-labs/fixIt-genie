# FixIt Buddy — Meta Ray-Ban Glasses Integration Plan

> **Status:** Ready for review — do not implement until approved
> **Research basis:** Actual source files read from local SDK clone at `/Users/monu/Documents/dev/meta-sdk/meta-wearables-dat-android`
> **SDK version:** Meta DAT 0.4.0 (released 2026-02-03)

---

## Why This Matters

FixIt Buddy's core UX is voice + vision — you describe a problem while pointing a camera at equipment. A phone is awkward for this: you're holding it with one hand while trying to fix something with the other. **Smart glasses solve this completely.**

- Camera sees exactly what you see — hands completely free
- Voice guidance plays directly in your ear
- Zero UI to look at — fully eyes-up experience
- Ray-Ban Meta: $224 (Gen 1 on Amazon), 2M+ units sold, widely recognized

The backend doesn't change at all. The ADK agent on Cloud Run doesn't care if JPEG frames come from a phone camera or glasses camera.

---

## How the Meta DAT SDK Works

### The 3-library SDK

```
mwdat-core     — device discovery, registration, permissions
mwdat-camera   — video streaming, photo capture
mwdat-mockdevice — testing without real hardware
```

### Connection Architecture

```
Ray-Ban Meta Glasses
    ↓ Bluetooth
Meta AI App (broker — must be installed on phone)
    ↓ local IPC
Your Android App (via DAT SDK)
    ↓ videoStream Flow (I420 frames)
GlassesCameraManager (new — converts I420 → JPEG)
    ↓ SharedFlow<ByteArray> (same as CameraManager today)
AgentWebSocket.sendVideoFrame()  ← NO CHANGE
    ↓ WebSocket
Cloud Run ADK Agent              ← NO CHANGE
    ↓
Gemini Vision + Voice Response   ← NO CHANGE
```

### The Actual API (verified from `StreamViewModel.kt` in SDK sample)

```kotlin
// Step 1: Initialize once (after Bluetooth permissions)
Wearables.initialize(context)

// Step 2: Register app with Meta (shows in-place dialog — no app switching since v0.4.0)
Wearables.startRegistration(activity)

// Step 3: Request camera permission from glasses
val status = Wearables.checkPermissionStatus(Permission.CAMERA)
// Returns DatResult<PermissionStatus, PermissionError>
registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
    val status = result.getOrDefault(PermissionStatus.Denied)
}.launch(Permission.CAMERA)

// Step 4: Start stream
val session = Wearables.startStreamSession(
    context,
    AutoDeviceSelector(),                               // picks first connected device
    StreamConfiguration(VideoQuality.MEDIUM, frameRate = 2)  // 2 FPS minimum
)

// Step 5: Collect frames — raw I420 format (NOT JPEG)
session.videoStream.collect { frame: VideoFrame ->
    val jpeg = i420ToJpeg(frame.buffer, frame.width, frame.height)
    _frames.tryEmit(jpeg)   // same SharedFlow<ByteArray> as CameraManager
}

// Step 6: Watch stream state
session.state.collect { state ->
    // StreamSessionState.STARTING | STREAMING | STOPPED
}
```

### Frame Format — I420 to JPEG (copy-paste from SDK sample)

Glasses output raw **I420** YUV frames, not JPEG. Must convert. Code is already in the sample's `StreamViewModel.kt`:

```kotlin
// I420 (YYY...UU...VV) → NV21 (YYY...VUVU) → JPEG
fun i420ToJpeg(buffer: ByteBuffer, width: Int, height: Int): ByteArray {
    val size = width * height
    val quarter = size / 4
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it); buffer.position(0) }
    val nv21 = ByteArray(bytes.size)
    bytes.copyInto(nv21, 0, 0, size)  // Y plane unchanged
    for (n in 0 until quarter) {
        nv21[size + n * 2]     = bytes[size + quarter + n]  // V
        nv21[size + n * 2 + 1] = bytes[size + n]            // U
    }
    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    return ByteArrayOutputStream().use { out ->
        yuv.compressToJpeg(Rect(0, 0, width, height), 75, out)
        out.toByteArray()
    }
}
```

### Mock Device Kit — Testing Without Hardware

The SDK ships a complete simulator. Verified working in `InstrumentationTest.kt`:

```kotlin
val kit = MockDeviceKit.getInstance(context)
val device = kit.pairRaybanMeta()  // creates simulated glasses
device.powerOn()
device.don()  // "put on" — required before stream starts
device.getCameraKit().setCameraFeed(uri)  // video file → becomes camera frames

// After this: session.videoStream emits frames from that video
// → full pipeline runs end-to-end, no real glasses needed
```

**Test video is already available locally** (no downloads needed):
```
/Users/monu/Documents/dev/meta-sdk/meta-wearables-dat-android/
  samples/CameraAccess/app/src/androidTest/assets/plant.mp4
```
The SDK's own instrumentation test (`InstrumentationTest.kt`) uses `plant.mp4` directly with `setCameraFeed()`.

---

## Critical Issues to Resolve

### 1. minSdk Must Bump: 26 → 31

**Current FixIt Buddy** (`android/app/build.gradle.kts` line 19): `minSdk = 26`
**DAT SDK requires** (`samples/CameraAccess/app/build.gradle.kts` line 25): `minSdk = 31`

Android 12 (API 31) is required. This is not optional — the SDK won't compile below 31.

**Impact:**
- Android 12+ market share: ~85% globally as of March 2026
- Our test device: Moto G Play 2023 = Android 13 ✅ — unaffected
- Users on Android 8–11 (~15%) will not be able to install the updated app

**Decision needed:** Bump minSdk to 31 (simpler, recommended) or keep 26 with runtime version guards (more complex, preserves backward compat but glasses won't work for old OS users anyway).

### 2. GitHub PAT Required for SDK Download

The SDK is hosted on GitHub Packages (private Maven). Needs a personal access token with `read:packages` scope. This goes in `android/local.properties` (gitignored):
```
github_token=ghp_xxxxxxxxxxxxxxxxxxxx
```

### 3. Meta Developer Account + APPLICATION_ID

Need to:
1. Create account at `developers.meta.com/wearables`
2. Create an organization + project
3. Get `APPLICATION_ID` (a string) for the manifest

**Shortcut for testing:** `APPLICATION_ID = "0"` works in Developer Mode + Mock Device Kit without registering. Confirmed in SDK's `AndroidManifest.xml` comment: *"Without Developer Mode, this key will need to be set with ID from app registered in Wearables Developer Center"*.

### 4. Meta AI App Required (for real glasses only)

The Meta AI app acts as a Bluetooth broker. It must be installed on the test phone for real glasses. **Not needed for Mock Device Kit.**

---

## What Changes in FixIt Buddy

### File 1: `android/settings.gradle.kts`

Add Meta's GitHub Maven repository. Currently only has `google()` and `mavenCentral()`.

```kotlin
// Add to dependencyResolutionManagement.repositories block:
maven {
    url = uri("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
    credentials {
        username = ""
        password = localProperties.getProperty("github_token") ?: System.getenv("GITHUB_TOKEN")
    }
}
```

Also need to add `local.properties` loading at the top of `settings.gradle.kts`:
```kotlin
import java.util.Properties
val localProperties = Properties().apply {
    val f = rootDir.resolve("local.properties")
    if (f.exists()) load(f.inputStream())
}
```

### File 2: `android/gradle/libs.versions.toml`

Add 3 entries (current file has no `mwdat` section):

```toml
[versions]
# ... existing entries ...
mwdat = "0.4.0"

[libraries]
# ... existing entries ...
mwdat-core       = { group = "com.meta.wearable", name = "mwdat-core",       version.ref = "mwdat" }
mwdat-camera     = { group = "com.meta.wearable", name = "mwdat-camera",     version.ref = "mwdat" }
mwdat-mockdevice = { group = "com.meta.wearable", name = "mwdat-mockdevice", version.ref = "mwdat" }
```

### File 3: `android/app/build.gradle.kts`

Two changes:

```kotlin
android {
    defaultConfig {
        minSdk = 31  // was 26 — required by Meta DAT SDK
        // all else unchanged
    }
}

dependencies {
    // ... existing deps unchanged ...
    implementation(libs.mwdat.core)
    implementation(libs.mwdat.camera)
    debugImplementation(libs.mwdat.mockdevice)  // debug builds only
}
```

### File 4: `android/app/src/main/AndroidManifest.xml`

Two new permissions + two meta-data entries:

```xml
<!-- Add permissions (INTERNET already present) -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Add inside <application> -->
<meta-data
    android:name="com.meta.wearable.mwdat.APPLICATION_ID"
    android:value="0" />
<meta-data
    android:name="com.meta.wearable.mwdat.ANALYTICS_OPT_OUT"
    android:value="true" />
```

### File 5: NEW `android/app/src/main/java/ai/fixitbuddy/app/core/camera/GlassesCameraManager.kt`

New class that wraps the DAT SDK. Emits `SharedFlow<ByteArray>` — **identical interface to `CameraManager`** — so `SessionViewModel` needs almost no changes.

Key responsibilities:
- Initialize `Wearables` SDK
- Handle Bluetooth permission request (Android runtime)
- Handle Meta camera permission request (via `Wearables.RequestPermissionContract`)
- Start/stop `StreamSession`
- Convert `VideoFrame` I420 → JPEG (copy I420→NV21→JPEG code from SDK sample)
- Throttle to avoid exceeding frame budget
- Mock Device Kit support in debug builds

This is **not a Hilt singleton** because it needs an `Activity` reference for permission launchers. It's created by `SessionViewModel` (or passed from `SessionScreen`).

### File 6: `android/app/src/main/java/ai/fixitbuddy/app/features/session/SessionViewModel.kt`

Two additions:

```kotlin
// 1. Add to SessionUiState:
data class SessionUiState(
    ...
    val cameraSource: CameraSource = CameraSource.PHONE,
)

enum class CameraSource { PHONE, GLASSES }

// 2. In startSession() Step 4 — select frame source:
val frameSource = when (_uiState.value.cameraSource) {
    CameraSource.GLASSES -> glassesCameraManager.frames
    CameraSource.PHONE   -> cameraManager.frames
}
launch { frameSource.collect { frame -> webSocket.sendVideoFrame(frame) } }

// 3. Add toggle function:
fun switchCameraSource(source: CameraSource) {
    _uiState.update { it.copy(cameraSource = source) }
}
```

### File 7: `android/app/src/main/java/ai/fixitbuddy/app/features/session/SessionScreen.kt`

Add a small toggle in the session controls area — glasses icon button that only appears when a glasses device is connected. Tapping it switches between `CameraSource.PHONE` and `CameraSource.GLASSES`.

---

## What Does NOT Change

| Component | Status | Reason |
|---|---|---|
| `AgentWebSocket.kt` | Unchanged | `sendVideoFrame(ByteArray)` already accepts JPEG bytes |
| `AudioStreamManager.kt` | Unchanged | Glasses mic/speaker use standard Bluetooth audio — Android handles it |
| `CameraManager.kt` | Unchanged | Phone camera path still works as-is |
| `SessionViewModel.startSession()` steps 1–3, 5 | Unchanged | REST session creation, WebSocket URL, audio forwarding |
| All backend — `agent.py`, `tools.py`, Cloud Run | Unchanged | Backend sees identical JPEG frames |
| All ADK protocol | Unchanged | |

---

## Effort Breakdown

| Task | Estimated Time | Complexity |
|---|---|---|
| GitHub PAT + Meta developer account + APPLICATION_ID | 30 min | Easy — one-time setup |
| `settings.gradle.kts` + `libs.versions.toml` changes | 30 min | Easy — copy from SDK sample |
| `build.gradle.kts` — deps + minSdk bump | 15 min | Easy |
| `AndroidManifest.xml` — permissions + meta-data | 15 min | Easy |
| `GlassesCameraManager.kt` | 3–4 hours | Medium — lifecycle + I420 conversion + permission flow |
| `SessionViewModel.kt` — source toggle | 1 hour | Easy |
| `SessionScreen.kt` — toggle UI | 1 hour | Easy |
| Mock Device Kit test setup with `plant.mp4` | 1–2 hours | Easy — video already in SDK |
| End-to-end test: mock → WebSocket → ADK → voice | 1–2 hours | Medium — first run always has surprises |
| **Total** | **~2 days** | |

---

## Mock Testing Flow (No Hardware Needed)

Can fully demo the glasses integration without owning glasses:

```
1. Build debug APK with mwdat-mockdevice dependency
2. Launch app on Moto G Play 2023
3. In Settings → tap "Connect Glasses" (debug only)
4. MockDeviceKit.pairRaybanMeta() → device.powerOn() → device.don()
5. device.getCameraKit().setCameraFeed(Uri pointing to plant.mp4)
6. Switch SessionScreen to "Glasses" camera source
7. Start session
8. Frames from plant.mp4 → GlassesCameraManager → WebSocket → ADK backend
9. Agent sees video frames, responds with voice — full pipeline verified
10. Audio plays through phone speaker (or Bluetooth earpiece)
```

---

## Risk Register

| Risk | Evidence | Likelihood | Mitigation |
|---|---|---|---|
| minSdk 26→31 drops ~15% of Android users | DAT SDK `build.gradle.kts:25` `minSdk = 31` | Certain | Glasses users all have Android 12+; drop is acceptable |
| Meta AI app must be installed (real glasses) | WearablesViewModel: permission routed through Meta AI app | Certain | Install from Play Store; mock kit bypasses entirely |
| `APPLICATION_ID = 0` may be rejected in production | Manifest comment: needs real ID without Developer Mode | Low risk for demo | Register real ID before shipping; `0` fine for hackathon demo |
| Frame rate minimum 2 FPS (was 1 FPS) | CHANGELOG 0.2.0: "Valid values include 30, 24, 15, 7 and 2 fps" | Certain | 2 FPS is fine; set `frameRate = 2` in `StreamConfiguration` |
| Latency through Meta AI app broker | Not measurable without hardware | Unknown | Can't mitigate without real glasses; mock bypasses broker |
| SDK compilation errors with AGP 9.0.1 | SDK uses AGP 8.6.0; FixIt Buddy uses AGP 9.0.1 | Low | SDK is just a library dep, not a plugin — AGP version irrelevant |
| Publishing to Play Store blocked (dev preview) | CHANGELOG note + Meta docs | Certain for now | Demo-only is fine for hackathon; broader publishing opens later 2026 |

---

## Verification Checklist

```
[ ] ./gradlew assembleDebug — builds clean, no unresolved deps
[ ] Logcat on launch — no Wearables SDK init errors
[ ] Mock device pairs successfully (debug menu)
[ ] Session starts with CameraSource.GLASSES selected
[ ] Logcat shows "GlassesCameraManager: emitting frame" every ~500ms
[ ] ADK backend receives frames (check Cloud Run logs)
[ ] Agent voice response plays through speaker
[ ] Switch back to CameraSource.PHONE — phone camera resumes
[ ] Stop session — stream closes cleanly, no crash
[ ] ./gradlew test — all existing unit tests still pass
```

---

## Future: Real Glasses

Buy Ray-Ban Meta Gen 1 (~$224 on Amazon):
1. Install Meta AI app on phone
2. Pair glasses in Meta AI app
3. Replace `APPLICATION_ID = "0"` with real ID from Wearables Developer Center
4. Run app — everything else is identical to mock testing

No code changes needed beyond the APPLICATION_ID swap.
