# FixIt Buddy — Demo Video Script (4 minutes max)

> Maps directly to judging criteria:
> - Innovation & Multimodal UX (40%)
> - Technical Implementation (30%)
> - Demo & Presentation (30%)

---

## 0:00–0:20 — Hook + Problem Statement (20 sec)

**Visual**: Split screen — person scrolling YouTube frustratedly on one side, person with phone pointed at equipment on the other.

**Narration**:
"Something breaks. You stand there scrolling through YouTube, trying to find a video that matches your exact situation. But tutorials can't see what YOU're looking at. What if you had a knowledgeable friend standing right there with you? That's FixIt Buddy."

**Show**: App icon + tagline "See. Hear. Fix."

---

## 0:20–0:45 — App Walkthrough + Onboarding (25 sec)

**Visual**: Screen recording of the app.

**Show the onboarding flow** — "See It. Say It. Fix It." pages. This is beautiful and shows polish.

**Narration**:
"FixIt Buddy is a native Android app powered by Google's Gemini Live API through the Agent Development Kit. Open the app, and a clean onboarding explains the three modes: see through your camera, say what's wrong, and the AI walks you through fixing it."

**Hit**: Show the "Let's Fix Something" button tap → transition to session screen.

---

## 0:45–1:45 — Demo Scenario 1: Car Engine (60 sec)

> This is the strongest visual demo. Do this one first.

**Visual**: Phone pointed at an open car hood (or a close-up of engine components).

**Show**:
1. Tap "Start Session" — status changes to "Listening"
2. Agent greets: "Hey! I'm FixIt Buddy. Point your camera at whatever needs fixing."
3. User says: "I think my oil is low, the oil light came on."
4. **Agent describes what it sees**: "I can see the engine bay. Let me look for the dipstick..."
   - THIS IS THE KEY MOMENT — the agent proving it's actually seeing through the camera (Innovation 40%)
5. Agent calls `get_safety_warnings` (show the status indicator change to "Using get_safety_warnings")
6. Agent says: "Before we check — the engine might be hot. Make sure it's been off for at least 10 minutes."
7. Agent calls `lookup_equipment_knowledge` — walks through the dipstick procedure
8. Agent says: "Pull the dipstick out, wipe it clean, reinsert, and pull out again. Tell me what you see."
9. User responds, agent confirms visually: "I can see the oil level — it's below the minimum mark."

**Narration callout**: "Notice how the agent describes what it sees, checks safety first, then guides one step at a time — confirming visually before moving on."

---

## 1:45–2:30 — Demo Scenario 2: Electrical Breaker Panel (45 sec)

**Visual**: Phone pointed at a home breaker panel.

**Show**:
1. User says: "My living room lost power."
2. Agent: "I can see the breaker panel. Let me look for any breakers in the middle position..."
3. Safety warning fires: "Never touch anything inside the panel with wet hands. Don't remove the panel cover."
4. Agent identifies a tripped breaker: "I can see one breaker that's not aligned — third row down on the left."
5. Walks through reset procedure: "Push it fully to OFF first, then flip to ON."
6. Agent: "If it trips again immediately, don't keep resetting — that means there's a fault on the circuit."

**Narration callout**: "The agent handles a completely different equipment category with the same natural conversation — automotive to electrical, zero reconfiguration."

---

## 2:30–3:00 — Demo Scenario 3: Washing Machine Error Code (30 sec)

**Visual**: Phone pointed at washing machine display showing an error code.

**Show**:
1. User says: "My washing machine is showing E4."
2. Agent calls `lookup_equipment_knowledge` with error code E4
3. Agent: "E4 is a water supply issue. Let me walk you through it — first, check that both supply valves behind the machine are fully open."
4. Agent calls `log_diagnostic_step` (show status indicator)

**Narration callout**: "33 error codes across automotive, electrical, and appliance categories. The knowledge base includes visual cues so the agent knows what to look for through the camera."

---

## 3:00–3:30 — Architecture + Technical Deep Dive (30 sec)

**Visual**: Architecture diagram (clean version from README).

**Narration**:
"Under the hood: a Kotlin Android app streams camera frames at 1 FPS and bidirectional 16kHz audio over a single WebSocket connection. The backend runs Google ADK on Cloud Run, using `gemini-2.5-flash-native-audio-preview` for real-time bidi-streaming with three custom function calling tools. Everything is deployed with a single IaC script."

**Flash**: Show the deploy.sh running, Cloud Run console, or `gcloud run deploy` output as proof of deployment.

---

## 3:30–3:50 — Safety + Responsible AI (20 sec)

**Visual**: Quick montage of safety warnings appearing in different scenarios.

**Narration**:
"Safety is non-negotiable. The agent always calls `get_safety_warnings` before guiding any physical action — electrical hazards, hot surfaces, chemical exposure. If the situation looks dangerous, it stops and recommends calling a professional. This is enforced in the system prompt and validated by unit tests."

---

## 3:50–4:00 — Closing (10 sec)

**Visual**: App screen with the tagline.

**Narration**:
"FixIt Buddy. See. Hear. Fix. People don't need more repair manuals — they need someone who can see what they're looking at and talk them through it."

**Show**: GitHub URL, team name.

---

## Production Notes

- **Record on a real Android device** (not emulator) — camera quality matters for the visual demos
- **Pre-configure the backend URL** so there's no setup time in the video
- **Have good lighting** for the equipment demos — the agent needs to see clearly
- **Show the status indicator** changing states (Listening → Thinking → Speaking) — this proves real-time processing
- **Include the tool call indicators** in the UI — judges need to see function calling happening live
- **Record audio from the phone speaker** if possible — hearing the agent's voice is more compelling than narration alone
- **Keep transitions fast** — 4 minutes goes quickly, don't waste time on screen transitions
- **Backup**: If live audio doesn't work well in recording, add narration over screen recording + show the transcript overlay as proof

## Timing Breakdown
| Segment | Duration | Judging Criteria |
|---------|----------|-----------------|
| Hook + Problem | 20 sec | Demo & Presentation (30%) |
| Onboarding | 25 sec | Innovation & Multimodal UX (40%) |
| Car engine demo | 60 sec | Innovation (40%) + Technical (30%) |
| Breaker panel demo | 45 sec | Innovation (40%) |
| Washing machine demo | 30 sec | Technical (30%) |
| Architecture | 30 sec | Technical (30%) |
| Safety + closing | 30 sec | Innovation (40%) + Demo (30%) |
| **Total** | **4:00** | |
