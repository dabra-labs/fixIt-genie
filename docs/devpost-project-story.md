# FixIt Genie - Devpost Project Story

## Inspiration

When something breaks, most people reach for a manual, a search engine, or a video that may not match the exact model in front of them. That works poorly when the problem is visual, safety-sensitive, or hands-on.

We wanted to build a different kind of assistant: one that can actually **see what the user sees**, hear the problem described naturally, and guide the repair step by step in real time. The Gemini Live API made that possible.

We also saw a bigger opportunity than home repair. The same interaction pattern applies to industrial maintenance, field service, automotive troubleshooting, and any workflow where someone needs expert guidance while their hands are busy. That vision pushed us to support not only a phone camera flow, but also **Ray-Ban Meta glasses** for hands-free live guidance.

## What it does

**FixIt Genie** is a real-time multimodal repair assistant built for the **Live Agents** category.

A user points their phone camera, or Ray-Ban Meta glasses, at broken equipment and describes the issue out loud. FixIt Genie:

- sees the equipment and visible problem through the live camera feed
- listens in natural conversation
- talks back with step-by-step repair guidance
- supports **live interruption**, so the user can say "wait" or "stop" mid-response
- uses a knowledge layer and supporting tools to answer equipment-specific questions
- puts safety first before recommending any physical action

In practice, that means it can identify appliances, read visible displays and error states, guide simple troubleshooting, and adapt the conversation in real time instead of behaving like a static chatbot.

## How we built it

We built FixIt Genie as a native Android app plus a Google Cloud backend.

### Android app

The Android client is built with **Kotlin** and **Jetpack Compose**. It handles:

- live camera capture with **CameraX**
- microphone streaming for real-time voice interaction
- audio playback for the agent's spoken responses
- a live transcript UI showing both user speech and agent responses
- a camera-first troubleshooting experience designed for real-world use

We also integrated **Ray-Ban Meta glasses** through the Android SDK so the same live agent can work in a hands-free mode.

### Backend agent

The backend is built with **Google ADK** and uses **Gemini 2.5 Flash Native Audio** for real-time multimodal conversation. It runs on **Google Cloud Run**, satisfying the Google Cloud deployment requirement.

The backend combines:

- Gemini Live for streaming voice and camera interaction
- domain-specific repair skills and reference content
- Firestore-backed retrieval for equipment guidance
- supporting tools for safety warnings, knowledge lookup, search, manuals, and long-tail troubleshooting

### Knowledge and grounding

We structured the system so the agent can stay conversational while still being grounded in repair-specific information. Instead of treating everything like a generic chatbot prompt, we separated:

- live conversation
- domain behavior
- retrieval and supporting tools

That made it easier to expand to new equipment categories and keep the system maintainable.

For long-tail cases, we also added tool support for:

- **Google Search** for rare error codes and model-specific procedures
- **manual lookup** for manufacturer troubleshooting steps
- **YouTube transcript-based guidance** for repair videos that are useful but too long to consume manually during a live session

## Challenges we ran into

The biggest challenge was making a live multimodal agent feel reliable.

A normal chat app can hide latency. A live voice-and-camera assistant cannot. We had to work through:

- real-time audio streaming and playback stability
- interruption behavior so the user could cut off the agent naturally
- session lifecycle issues across longer live runs
- getting the on-screen transcript to reflect the real conversation clearly
- balancing fast answers with equipment-specific grounding
- reading real-world displays, which can be reflective, noisy, and ambiguous on camera
- making the full experience feel demo-ready rather than just technically functional

We also learned that building a strong live agent is not only about model quality. It is equally about orchestration, recovery, latency, camera framing, and user trust.

## Accomplishments that we're proud of

We're proud that FixIt Genie is not just a chatbot with a camera attached. It is a real **live agent** with a multimodal interaction loop.

Highlights we're proud of:

- built a working **Gemini Live** experience with voice, camera, and natural conversation
- implemented **interruptible live interaction**
- deployed the backend on **Google Cloud Run**
- added **Ray-Ban Meta glasses** integration for hands-free repair workflows
- created a camera-first Android UX that shows the conversation and guidance clearly
- structured the backend with Google ADK, retrieval, and safety-oriented tools instead of a single monolithic prompt
- demonstrated a product direction that can scale beyond home repair into industrial and field-service scenarios

## What we learned

We learned that multimodal live agents are a different product category from traditional AI chat.

A few lessons stood out:

- **trust matters immediately**: users want to know the agent is actually seeing what they are showing
- **interruptibility matters**: live conversation feels broken if the user cannot naturally cut in
- **low-latency UX matters as much as model quality**
- **safety needs to be designed in**, not added later
- **structured knowledge plus tools beat a giant prompt** for domain-specific workflows
- **hands-free input changes the value completely** in real-world repair and field-service scenarios

Most importantly, we learned that once AI can see, hear, and respond in real time, the experience becomes much closer to working with a knowledgeable partner than using a chatbot.

## What's next for FixIt Genie - Your Live AI Repair Assistant

We want to turn FixIt Genie from a strong live prototype into a more robust repair platform for both consumers and professionals.

What's next:

- improve reliability and recovery for longer live sessions
- expand equipment coverage and model-specific knowledge
- make the agent better at asking for the exact camera angle it needs
- strengthen grounded web search for rare error codes and model-specific procedures
- improve YouTube-based repair guidance so niche repair videos are more reliable in live sessions
- expand manual and reference retrieval for better manufacturer-specific troubleshooting
- deepen support for industrial and field-service workflows
- continue improving the Ray-Ban Meta glasses experience for hands-free repair
- keep refining the UI so live conversation, visuals, and repair steps feel even more polished

FixIt Genie starts with home repair, but the bigger vision is a live, hands-free AI assistant for real-world troubleshooting anywhere people need expert guidance in the moment.
