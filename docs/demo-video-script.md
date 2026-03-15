# FixIt Genie — Final Demo Script
**Total: 4:00 mins · Live audio · One continuous session**

> Judging: Innovation & Multimodal UX (40%) · Technical (30%) · Demo (30%)

---

## TIMING

| Segment | Time | Duration |
|---------|------|----------|
| Pitch | 0:00 | 0:30 |
| Webapp + onboarding | 0:30 | 0:20 |
| Architecture flash | 0:50 | 0:10 |
| Demo — Fridge | 1:00 | 1:00 |
| Demo — walk to washer | 2:00 | 0:10 |
| Demo — LG washing machine | 2:10 | 0:50 |
| Demo — Breaker panel | 3:00 | 0:45 |
| Close | 3:45 | 0:15 |

---

## 0:00–0:30 — PITCH

**[Face to camera. Confident. Fast.]**

> *"When something breaks, you're stuck scrolling YouTube hoping you find a video that matches your exact situation. But those videos can't see what you're looking at.*
>
> *FixIt Genie can. It watches through your camera, listens to you describe the problem, and talks you through the fix — step by step, in real time.*
>
> *The vision: a technician with hands inside an electrical panel, Ray-Ban glasses on, an AI agent watching through the camera — diagnosing live, talking them through it, hands-free. That's where this is going.*
>
> *Built with Gemini Live API and Google ADK. Let me show you."*

---

## 0:30–0:50 — WEBAPP + ONBOARDING

**[Screen record — fixit-genie.web.app on desktop. 5 seconds.]**

**[Cut to phone — swipe through 3 onboarding screens quickly. No narration needed.]**

> Page 1: FixIt Genie — Your AI repair genie
> Page 2: See It. Say It. Fix It.
> Page 3: Built for Real Repairs

**[Tap "Let's Fix Something" → session idle screen. Keep moving.]**

---

## 0:50–1:00 — ARCHITECTURE FLASH

**[Show architecture diagram — 5 seconds. Voice over:]**

> *"Android app. Google ADK backend on Cloud Run. Gemini 2.5 Flash Native Audio for real-time bidi-streaming. Six tools. One WebSocket."*

**[Flash Cloud Run console — green status. 3 seconds. Move on.]**

---

## 1:00–2:00 — DEMO 1: LG FRIDGE NOT COOLING

**Setup:** LG fridge door open, interior visible. Session already running — genie avatar pulsing.

---

**[YOU SAY]**
> *"Alright — my fridge has been warm for two days. Food's starting to go bad. Let me show you."*

*[Point camera inside fridge. Hold 2-3 seconds.]*

---

**[AGENT RESPONDS — let it speak]**

---

**[YOU SAY]**
> *"It's an LG. The freezer is totally fine — just the fridge section is warm."*

*[Watch for tool call chip flashing on screen — hold still so judges see it.]*

---

**[AGENT RESPONDS — diagnosing evaporator fan. Mid-response, interrupt:]**

**[YOU SAY — cut across the agent]**
> *"Wait — can you back up? What's actually causing this?"*

*[Agent stops immediately. This proves live interruption — the #1 Live Agent judging moment. Let it land.]*

---

**[AGENT RESPONDS — resumes explanation]**

---

**[YOU SAY]**
> *"Got it. What do I check first?"*

*[Point camera at back wall vents inside fridge.]*

---

**[AGENT RESPONDS — step by step. CUT after first step.]**

---

## 2:00–2:10 — WALK TO WASHER

**[Keep camera rolling. Walk from fridge to washing machine. Say:]**

> *"Actually — different problem over here while I have you."*

*[This shows persistent session, topic switch, agent stays live. No restart needed.]*

---

## 2:10–3:00 — DEMO 2: LG WASHING MACHINE DE1

**Setup:** LG washer, door slightly ajar, DE1 showing on display.

---

**[YOU SAY]**
> *"My LG washer is throwing this error and won't start."*

*[Point camera at display showing DE1. Hold 2 seconds.]*

---

**[AGENT RESPONDS — reads error code, tool call fires]*

---

**[YOU SAY]**
> *"The door's slightly open — is that it?"*

*[Pan camera to show door slightly ajar.]*

---

**[AGENT RESPONDS — confirms DE1 = door error, guides you to check latch and seal]**

---

**[YOU SAY]**
> *"What am I looking for on the latch?"*

*[Open door fully, point camera at latch mechanism and rubber seal.]*

---

**[AGENT RESPONDS — CUT after diagnosis.]**

---

## 3:00–3:45 — DEMO 3: TRIPPED BREAKER

**Setup:** Breaker panel open. One breaker visibly tripped (middle position).

---

**[YOU SAY]**
> *"One more. Half my kitchen outlets just died."*

*[Point camera at full panel. Hold 2 seconds.]*

---

**[AGENT RESPONDS — safety warning fires. DO NOT CUT THIS. Let it play fully.]*

*[This is a judging-critical moment — automatic safety warnings before any physical action.]*

---

**[YOU SAY]**
> *"Understood. Can you see anything wrong from here?"*

---

**[AGENT RESPONDS — identifies tripped breaker by position]**

---

**[YOU SAY]**
> *"That one — what do I do?"*

*[Point camera directly at the tripped breaker.]*

---

**[AGENT RESPONDS — OFF first, then ON. CUT after instructions.]**

---

**[YOU SAY — sells the close]**
> *"That worked. Kitchen's back."*

---

## 3:45–4:00 — CLOSE

**[Return to app. Genie avatar on screen.]**

**[YOU SAY]**
> *"Three problems. Three rooms. One conversation.*
> *FixIt Genie — because people don't need more repair manuals. They need someone who can see what they're looking at."*

*[Hold on app. Fade out.]*

---

## BEFORE YOU FILM — CHECKLIST

**Props**
- [ ] Fridge door accessible, interior visible, back vents showing
- [ ] LG washer — door slightly ajar, DE1 showing on display (or tape "DE1" on panel)
- [ ] Breaker panel open, one breaker pre-flipped to tripped position

**App**
- [ ] Backend URL correct in Settings — test session starts before filming
- [ ] Wi-Fi only (not mobile data)
- [ ] AirPods or earphones in — no echo
- [ ] Session already running before you walk to each scene

**Camera**
- [ ] Screen recorder running on phone OR second device capturing the phone screen
- [ ] Good lighting at each scene — especially inside the fridge and breaker panel
- [ ] Hold camera steady 2-3 seconds after pointing at each item — give Gemini time to process

---

## THE 4 MOMENTS JUDGES WILL REWATCH

| Moment | Why it matters |
|--------|---------------|
| Agent identifies "freezer fine, fridge warm" → evaporator fan — without you naming it | Proves real vision working |
| You interrupt mid-sentence → agent stops instantly | Proves true bidi-streaming |
| Safety warning fires automatically on breaker panel | Safety-first architecture |
| Tool call chip flashes during error code lookup | Function calling mid-conversation |

---

## IF SOMETHING GOES WRONG

- **Agent doesn't respond:** Say *"can you continue?"* — keep rolling
- **Wrong answer:** Say *"let me show you more"* and move camera — agent course-corrects
- **Connection drops:** Cut, reconnect off-camera, resume — edit it out
- **Agent talks too long:** Interrupt it — that's actually a better demo moment
