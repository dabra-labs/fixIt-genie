# FixIt Genie — Rebrand & Avatar Design Spec

**Date:** 2026-03-09
**Status:** Approved

---

## What & Why

FixIt Buddy → **FixIt Genie**. The "Genie" metaphor is richer: a genie appears when you need it, knows everything, grants your wish (fixing the broken thing). "Buddy" is generic; "Genie" is memorable and demo-worthy.

The avatar gives the AI a face and personality. People engage better with a character than a status chip. The key insight that shaped the design: **the user's eyes are on the equipment in the real world, not on the screen**. The camera feed is for the model, not the user. So the screen is about the *conversation with the genie*, not a viewfinder.

---

## Design

### Screen Layout

```
┌────────────────────────────┐
│  [●] Speaking    FixItGenie│  ← status bar (32px)
│                            │
│   camera feed (~58%)       │  ← CameraX PreviewView (background)
│   model sees this          │
│                            │
├────────────────────────────┤  ← gradient fade, no hard line
│  🧞  [chat bubbles]        │
│  ~~  You: "what's wrong?"  │  ← ~42% of screen (200dp)
│  ~~  Genie: "That cap..."  │
│      [typing cursor]       │
├────────────────────────────┤
│  🔦 👓          🎤 End    │  ← compact controls (52px)
└────────────────────────────┘
```

### Genie Avatar Component

Pure Compose Canvas — no Lottie, no bitmaps, no new dependencies.

| Layer | Implementation |
|-------|---------------|
| Golden aura | `drawCircle` with radial alpha, `infiniteRepeatable(tween(2400ms))` scale 1.0→1.15 |
| Face | `drawCircle` with radial gradient (cream→orange), golden border, floating hover animation |
| Purple smoke tail | `drawOval` with vertical gradient purple→transparent |
| Ripple rings | 3× `drawCircle` with expanding radius + fading alpha (staggered 660ms each) |

**Ripple rings driven by `audioLevel: Float`** — ring expansion speed and opacity scale with audio amplitude. Silent = barely perceptible slow pulse. Speaking loud = fast vivid rings.

### Transcript — Chat Bubbles

Replace `TranscriptOverlay` (single scrolling text block) with a two-sided chat layout:

- **User turns** — right-aligned, dim white, semi-transparent pill background
- **Genie turns** — left-aligned, bright white, purple-tinted pill background
- **Live typing cursor** — blinking purple rectangle at end of current genie response
- **Auto-scroll** to bottom as new content arrives
- Show last 3–4 exchanges; older ones scroll away

### Controls (Compact)

Replace 70px control bar with 52px:
- Left: `🔦` flashlight icon + `👓` glasses icon (32dp circular buttons)
- Right: End session pill (80×34dp, red `#B71C1C`)
- "Start Session" state: single centered Start pill

---

## Files to Change

| File | Change |
|------|--------|
| `res/values/strings.xml` | `app_name` → "FixIt Genie", update all UI strings |
| `design/Color.kt` | No change needed — orange/purple palette already fits |
| `features/session/SessionScreen.kt` | Replace bottom control bar + transcript overlay with new layout |
| `features/session/components/TranscriptOverlay.kt` | Replace with `GenieTranscript.kt` (chat bubbles) |
| `features/session/components/StatusIndicator.kt` | Minor: update app name reference if any |
| New: `features/session/components/GenieAvatar.kt` | Compose Canvas genie face + ripple rings |
| New: `features/session/components/GenieTranscript.kt` | Chat bubble transcript |
| `features/session/SessionViewModel.kt` | Parse transcript into user/genie turns |

---

## Data Model

The existing `transcript: String` is a single concatenated stream. To support chat bubbles, parse it into turns:

```kotlin
data class ChatTurn(val role: Role, val text: String)
enum class Role { USER, GENIE }
```

The transcript already alternates user/genie — split on turn boundaries in the ViewModel.

---

## Reused Existing State (no new state needed)

- `SessionState` (Idle/Connecting/Listening/Thinking/Speaking/Error) → drives genie animation state
- `audioLevel: Float` → drives ripple ring amplitude
- `transcript: String` → parsed into `List<ChatTurn>`
- `toolCallEvent` → unchanged, keeps existing chip

---

## Verification

1. Build debug APK: `cd android && ./gradlew assembleDebug`
2. Launch on device — confirm app name shows "FixIt Genie"
3. Start session — genie avatar appears, aura breathes
4. Speak — ripple rings animate, user bubble appears
5. Genie responds — genie bubble appears with live typing cursor, rings animate with audio
6. End session — returns to idle state cleanly
7. Check no jank: GPU profiling should show avatar well under 16ms frame budget
