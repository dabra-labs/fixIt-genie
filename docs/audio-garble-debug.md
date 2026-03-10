# Audio Garble / Truncation — Debug Log

## Problem Statement

Agent voice output from Gemini Live API is garbled or truncated near the **end of responses**. The first portion of each response is often audible and clear, but the tail end cuts off or plays distorted noise.

Volume was also too low initially — **that is now fixed** (see Fix #2 below). Garbling at end of responses remains unresolved.

---

## Relevant Files

| File | Role |
|------|------|
| `android/app/src/main/java/ai/fixitbuddy/app/core/websocket/AgentWebSocket.kt` | Parses ADK LiveEvent JSON, emits `AgentMessage` to ViewModel |
| `android/app/src/main/java/ai/fixitbuddy/app/core/audio/AudioStreamManager.kt` | AudioRecord (mic in) + AudioTrack (speaker out), playback queue |
| `android/app/src/main/java/ai/fixitbuddy/app/features/session/SessionViewModel.kt` | Orchestrates session, routes messages, controls mic gate (`agentSpeaking`) |

---

## ADK Protocol — What the Server Actually Sends

Captured from logcat on Moto G Play 2023 (Android 13):

```
// Audio content message — NO `partial` field:
{"content":{"parts":[{"inlineData":{"mimeType":"audio/pcm","data":"..."}}]}}

// Transcription message — HAS `partial: true`:
{"partial":true,"outputTranscription":{"text":"Hello! How can I help..."}}

// Interrupt message:
{"interrupted":true}
```

Key observations:
- Audio content frames do **not** include the `partial` field
- `partial` only appears on transcription events
- There is no explicit "end of turn" marker on audio frames
- The server sends many audio content frames, then eventually stops
- `interrupted: true` is sent when VAD detects user speech mid-playback

---

## Fixes Applied (Chronological)

### Fix #1 — URL-safe base64 decode (FIXED — working)

**Symptom**: No voice output, or pure static noise
**Root cause**: ADK sends audio data as URL-safe base64 (`-` and `_` chars). We decoded with `Base64.NO_WRAP` which silently corrupted these characters.
**Fix**: Changed to `Base64.URL_SAFE` in `AgentWebSocket.kt:174`

```kotlin
// Before (broken):
val audioBytes = Base64.decode(data, Base64.NO_WRAP)

// After (fixed):
val audioBytes = Base64.decode(data, Base64.URL_SAFE)
```

**Status**: ✅ FIXED — voice is now audible

---

### Fix #2 — Volume too low: USAGE_MEDIA vs USAGE_VOICE_COMMUNICATION (FIXED — working)

**Symptom**: Could barely hear agent even at max volume
**Root cause**: `USAGE_VOICE_COMMUNICATION` maps to `STREAM_VOICE_CALL` — only **5 volume steps** on Moto G Play 2023. Android 13+ regression makes `setStreamVolume(STREAM_VOICE_CALL)` have no effect.
**Fix**: Switched AudioTrack to `USAGE_MEDIA` (maps to `STREAM_MUSIC`, 15 volume steps, 3× louder on loudspeaker). AcousticEchoCanceler is attached to `AudioRecord.audioSessionId`, not AudioTrack — AEC is unaffected.

```kotlin
// AudioStreamManager.kt — initPlayback()
// Before:
.setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
// After:
.setUsage(AudioAttributes.USAGE_MEDIA)
```

Also removed: `audioManager.mode = AudioManager.MODE_IN_COMMUNICATION`, `audioManager.isSpeakerphoneOn = true`, `setStreamVolume(STREAM_VOICE_CALL, ...)` calls.

**Status**: ✅ FIXED — volume is now adequate

---

### Fix #3 — `interrupted` signal not handled (APPLIED — partial effect)

**Symptom**: When user speaks mid-agent-response, old + new audio overlap → garble
**Root cause**: `parseAdkEvent()` returned early when `content` was absent, silently dropping `{"interrupted": true}` messages. Stale audio kept playing.
**Fix**: Added interrupt check before the `content` guard; added `AgentMessage.Interrupted` to sealed class; `AudioStreamManager.interrupt()` cancels old queue and creates new one.

```kotlin
// AgentWebSocket.kt — parseAdkEvent()
if (obj["interrupted"]?.jsonPrimitive?.content == "true") {
    _incomingMessages.tryEmit(AgentMessage.Interrupted)
    return
}
```

```kotlin
// AudioStreamManager.kt
fun interrupt() {
    val oldChannel = playbackChannel
    val ch = Channel<ByteArray>(capacity = 50)
    playbackChannel = ch
    oldChannel?.cancel()
    playbackJob?.cancel()
    playbackJob = playbackScope.launch { for (chunk in ch) { audioTrack?.write(...) } }
}
```

**Status**: ⚠️ APPLIED — may help when user interrupts, but doesn't fix end-of-turn truncation

---

### Fix #4 — Playback buffer too small (APPLIED — unclear effect)

**Symptom**: Possible underrun causing gaps/truncation
**Fix**: Increased `AudioTrack` buffer from `bufferSize * 4` to `bufferSize * 8`

**Status**: ⚠️ APPLIED — no noticeable improvement on garble

---

### Fix #5 — Mic gate (`agentSpeaking` flag) (APPLIED — AEC improvement)

**Symptom**: Agent voice echoing back through mic → backend treats it as user input → interrupts self
**Fix**: Added `agentSpeaking` volatile flag in `SessionViewModel`. Set to `true` on `AgentMessage.Audio`, cleared 400ms after `Status("listening")`. Mic chunks suppressed while `agentSpeaking == true`.

**Status**: ⚠️ APPLIED — but may have a bug (see Current Theory below)

---

## Current Theory — Why Garble Persists

### The `partial` field problem

`isPartial` in `parseAdkEvent()` is computed as:
```kotlin
val isPartial = obj["partial"]?.jsonPrimitive?.content == "true"
```

Since audio content frames **do not include `partial`**, `isPartial` is always `false` for audio messages. This means:

```kotlin
// After every single audio chunk (even mid-response):
_incomingMessages.tryEmit(AgentMessage.Status("listening"))  // ← fires on EVERY chunk
```

This causes the mic gate to **open 400ms after each audio chunk** mid-response:
1. Agent sends audio chunk #1 → mic gate starts 400ms countdown
2. More audio chunks arrive (still playing) — mic is unmuted 400ms after chunk #1
3. AudioTrack plays agent voice with mic open → AEC has no reference → echo
4. Echo reaches backend → VAD fires `interrupted` → new response starts
5. Old + new audio overlap → garble at end

### Consequence chain
```
audio chunk received → Status("listening") emitted →
400ms delay → agentSpeaking = false → mic opens →
agent audio plays into open mic → AEC fails →
backend hears echo → VAD interrupt → new response starts →
old response tail + new response start = garble
```

### What correct behavior should look like
The mic gate should only open **after the last audio chunk of a complete turn**, not after every chunk. We need a reliable "end of turn" signal from the ADK server.

---

## Next Steps to Try (Unimplemented)

### Option A — Fix `Status("listening")` emission (highest priority)

Only emit `Status("listening")` when there's a clear turn-end signal, not just when `isPartial` is false on an audio chunk.

```kotlin
// In parseAdkEvent() — only emit listening status when content has no audio parts
// and author is the agent
val hasAudio = parts.any { it.jsonObject["inlineData"] != null }
if (author == "fixitbuddy" && !hasAudio && !isPartial) {
    _incomingMessages.tryEmit(AgentMessage.Status("listening"))
}
```

Or: completely decouple the `agentSpeaking` flag from `Status("listening")`. Instead, use a timer — if no new audio chunk arrives within N ms, assume turn is over.

### Option B — Timer-based turn detection

```kotlin
// In SessionViewModel
private var lastAudioChunkMs = 0L
private var turnEndJob: Job? = null

is AgentMessage.Audio -> {
    agentSpeaking = true
    lastAudioChunkMs = System.currentTimeMillis()
    audioManager.playAudioChunk(message.data)
    turnEndJob?.cancel()
    turnEndJob = launch {
        delay(800)  // if no new audio chunk in 800ms, turn is probably over
        agentSpeaking = false
    }
}
```

### Option C — Don't suppress mic at all; rely on backend VAD

The Gemini backend uses VAD natively. The `interrupted` signal exists precisely so the client can clear its queue when VAD fires. If AEC is working well enough (hardware AEC on `VOICE_COMMUNICATION` AudioRecord source), mic suppression may not be needed. Remove `agentSpeaking` gate entirely and trust the server.

### Option D — Switch to `outputTranscription` for turn detection

The server does send `{"partial":true,"outputTranscription":{...}}` and eventually `{"partial":false,"outputTranscription":{...}}` (or similar). The `partial: false` on a transcription event likely indicates true end of turn.

---

## Reference — ADK Audio Flow

```
Server (ADK/Gemini)                   Client (Android)
─────────────────────                 ────────────────
Send: content.parts[].inlineData  →   decode base64 → AudioTrack.write()
Send: content.parts[].text        →   update transcript
Send: outputTranscription         →   update transcript (with partial flag)
Send: interrupted: true           →   cancel queue, reset agentSpeaking

AudioRecord (mic) ────────────────→   send: blob.data (base64 PCM 16kHz)
```

## Reference — Relevant Upstream Examples

- `gemini-live-api-examples/gemini-live-ephemeral-tokens-websocket/frontend/geminilive.js` — JavaScript client showing `serverContent.interrupted` check, `turnComplete`, message parsing
- `gemini-live-api-examples/command-line/node/main.mts` — Node.js example, awaits `speaker.write()` completion before stopping
- ADK Python agent: audio output is 24kHz, 16-bit PCM, mono (confirmed in ADK docs)

---

## Device Info

- **Phone**: Motorola Moto G Play 2023
- **Android**: 13
- **App**: FixIt Buddy (debug APK, `ai.fixitbuddy.app`)
- **Backend**: Google Cloud Run — `https://fixitbuddy-agent-hybxqwgczq-uc.a.run.app`
- **Model**: `gemini-2.5-flash-native-audio-preview-12-2025` via Gemini API

---

*Last updated: 2026-03-10*
