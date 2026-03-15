# FixIt Genie — Demo Video Script

**Target:** Under 4 minutes · Live audio · No mockups · No AI voiceover needed

> Judging weights: Innovation & Multimodal UX (40%) · Technical (30%) · Demo (30%)

---

## TIMING OVERVIEW

| Segment | Time | Duration |
|---------|------|----------|
| Hook + Intro | 0:00 | 20s |
| Onboarding | 0:20 | 20s |
| Fridge demo | 0:40 | 90s |
| Electrical panel | 2:10 | 60s |
| LG washing machine | 3:10 | 30s |
| Architecture proof | 3:40 | 30s |
| Close | 4:10 | 15s |

---

## 0:00–0:20 — HOOK

**[No talking. Text overlays on black screen.]**

> *"What if AI could see what you see?"*
> *"And talk you through fixing it — in real time."*

Cut to: app launch screen, genie lamp glowing.

**[YOU SAY — direct to camera, 10 seconds]**
> *"Most repair guides can't see what you're looking at. FixIt Genie can. It watches through your camera, listens to you, and guides you through the fix — step by step, hands-free. Built with Gemini Live API and Google ADK. Here's a real session."*

---

## 0:20–0:40 — ONBOARDING (screen recording)

Swipe through all 3 onboarding pages quickly. No narration needed — the screens speak for themselves.

End on: tap **"Let's Fix Something"** → session idle screen appears.

---

## 0:40–2:10 — SCENE 1: FRIDGE NOT COOLING

**Setup:** Fridge door open, interior visible. Session running, genie avatar pulsing.

---

**[YOU SAY]**
> *"My fridge has been warm for two days. Food's starting to go bad. Let me show you what I'm dealing with."*

*[Point camera inside the fridge. Hold 2-3 seconds — show interior, back vents, any display panel.]*

---

**[AGENT RESPONDS — will identify the situation visually. Let it finish.]**

---

**[YOU SAY]**
> *"It's a Samsung. The weird thing is — the freezer is totally fine. Just the fridge section is warm."*

*[Watch for the tool call indicator — "Using lookup_equipment_knowledge" or "Using google_search" will flash on screen. Let it show. This is a judging moment.]*

---

**[AGENT RESPONDS — will diagnose evaporator fan issue. Let it speak.]**

---

**[YOU SAY — interrupt mid-response]**
> *"Wait — actually, can you back up? What's causing this exactly?"*

*[This demonstrates live interruption. The agent stops immediately and responds to your question. This is the #1 Live Agent judging criteria — don't skip this moment.]*

---

**[AGENT RESPONDS — resumes explanation after interruption]**

---

**[YOU SAY]**
> *"How do I check the fan? Show me what to look for."*

*[Point camera at the back wall vents inside the fridge compartment.]*

---

**[AGENT RESPONDS — gives step-by-step, visually confirms what it sees]**

---

**[CUT at 2:10]**

Text card: *"Different problem. Different room."*

---

## 2:10–3:10 — SCENE 2: TRIPPED BREAKER

**Setup:** Breaker panel door open. One breaker visibly tripped (middle position). Phone pointed at panel.

---

**[YOU SAY]**
> *"Half the outlets in my kitchen stopped working. Found this panel — something looks off."*

*[Point camera at the full panel. Hold 2 seconds. Let agent scan.]*

---

**[AGENT RESPONDS — safety warning fires immediately. Do NOT cut this. Let it run fully. It's a feature.]*

---

**[YOU SAY]**
> *"Understood. Now — can you see anything wrong from here?"*

*[Hold camera steady on the panel.]*

---

**[AGENT RESPONDS — identifies the tripped breaker by position]**

---

**[YOU SAY]**
> *"That one? What do I do?"*

*[Point camera directly at the tripped breaker.]*

---

**[AGENT RESPONDS — gives reset procedure: OFF first, then ON]**

---

**[YOU SAY — optional, sells the moment]**
> *"That worked. Kitchen's back."*

---

**[CUT at 3:10]**

---

## 3:10–3:40 — SCENE 3: LG WASHING MACHINE DOOR ERROR

**Setup:** LG washing machine with door slightly ajar. Display showing DE1 error. Session running.

---

**[YOU SAY]**
> *"New problem. My LG washer is throwing an error and won't start."*

*[Point camera at the control panel display showing DE1. Hold 2 seconds.]*

---

**[AGENT RESPONDS — will read the error code visually. Tool call fires: knowledge base lookup.]*

---

**[YOU SAY]**
> *"The door is slightly open — could that be it?"*

*[Pan camera to show the door slightly ajar — make it visible.]*

---

**[AGENT RESPONDS — confirms DE1 = door error, will ask you to check the latch and seal]**

---

**[YOU SAY]**
> *"What am I looking for exactly?"*

*[Open door fully, point camera at the door latch and rubber gasket seal.]*

---

**[AGENT RESPONDS — visually guides you through checking the latch mechanism and seal for obstructions]**

---

**[CUT at 3:40]**

Text card: *"Three problems. Three rooms. One conversation."*

---

## 3:40–4:10 — ARCHITECTURE PROOF

**[Screen share: show architecture diagram from README or Cloud Run console with green status]**

**[YOU SAY — keep it tight]**
> *"Under the hood: Android app streams live camera frames at 1 FPS and two-way audio over a single WebSocket. The backend is Google ADK running on Cloud Run — Gemini 2.5 Flash Native Audio for the live session, six custom tools for knowledge lookup, web search, and YouTube. One IaC script deploys the whole backend."*

*[Flash: Cloud Run console showing service is Ready. 2 seconds is enough.]*

---

## 3:40–4:00 — CLOSE

**[Return to app. Genie avatar on screen.]**

**[YOU SAY]**
> *"See it. Say it. Fix it. FixIt Genie — because people don't need more repair manuals. They need someone who can see what they're looking at."*

*[Hold on app screen. Fade out.]*

---

## PRODUCTION CHECKLIST

### Before you film
- [ ] Backend URL set in app Settings — test it connects before rolling
- [ ] Wi-Fi only, not mobile data — WebSocket needs stable low latency
- [ ] AirPods or earphones in — prevents echo going back into the mic
- [ ] One breaker pre-flipped to tripped position
- [ ] Fridge door accessible and interior visible
- [ ] Genie session running before you walk to each scene (don't film the "Start Session" tap each time)
- [ ] Second device or screen recorder ready to capture phone screen

### While filming
- **Pause 2 seconds after pointing the camera** — give Gemini time to process the frame before speaking
- **Talk naturally** — casual tone, like texting a knowledgeable friend
- **Don't rush the safety warning** — let it play fully, it's a judging-relevant feature
- **Show the tool call chip** — when "Using lookup_equipment_knowledge" flashes, hold still for 1 second so judges see it
- **Do the interruption** — mid-fridge-response, say "wait — can you back up?" — this proves bidi-streaming works live

### The 3 moments judges will rewatch
1. Agent identifies "freezer fine, fridge warm" → evaporator fan diagnosis **without you naming it**
2. You interrupt mid-sentence → agent stops immediately
3. Safety warning fires automatically on the electrical panel

---

## IF SOMETHING GOES WRONG

- **Agent doesn't respond:** Say "can you continue?" — natural recovery, keep rolling
- **Wrong diagnosis:** Say "let me show you more" and move the camera — the agent will course-correct
- **Connection drops:** Cut the clip, reconnect off-camera, resume — edit it out later
- **Agent talks too long:** Interrupt it — that's actually a better demo moment than waiting
