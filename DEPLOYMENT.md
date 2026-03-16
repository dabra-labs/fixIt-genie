# FixIt Genie — Cloud Deployment Proof

This file documents the live Google Cloud deployment of the FixIt Genie backend for judges.

---

## Live Service

| Field | Value |
|-------|-------|
| **App Name** | FixIt Genie (Cloud Run service name `fixitbuddy-agent` is from early development) |
| **Service URL** | https://fixitbuddy-agent-hybxqwgczq-uc.a.run.app |
| **Platform** | Google Cloud Run (managed) |
| **Region** | us-central1 |
| **Status** | ✅ Ready |
| **Current Revision** | fixitbuddy-agent-00010-pf7 |
| **Memory / CPU** | 2 GiB / 2 vCPU |
| **Model** | gemini-2.5-flash-native-audio-latest |

---

## Deployment History

The backend has been deployed **10 times** across the hackathon build — each deploy via `backend/deploy.sh`.

| Revision | Deployed |
|----------|----------|
| fixitbuddy-agent-00010-pf7 | 2026-03-10 18:04 UTC |
| fixitbuddy-agent-00009-425 | 2026-03-10 14:30 UTC |
| fixitbuddy-agent-00008-mtr | 2026-03-09 14:24 UTC |
| fixitbuddy-agent-00007-jn5 | 2026-03-09 14:16 UTC |
| fixitbuddy-agent-00006-l59 | 2026-03-09 14:13 UTC |
| fixitbuddy-agent-00005-lbk | 2026-03-09 14:12 UTC |
| fixitbuddy-agent-00004-fgz | 2026-03-09 13:31 UTC |
| fixitbuddy-agent-00003-gqn | 2026-03-09 12:05 UTC |
| fixitbuddy-agent-00002-l4t | 2026-03-09 11:49 UTC |
| fixitbuddy-agent-00001-d26 | 2026-03-09 05:30 UTC |

---

## Google Cloud Services Used

| Service | How It's Used | Code Reference |
|---------|--------------|----------------|
| **Cloud Run** | Hosts the ADK agent with persistent WebSocket + session affinity | [deploy.sh](backend/deploy.sh) |
| **Cloud Build** | Builds the Docker container on deploy | [deploy.sh#L73](backend/deploy.sh#L73) |
| **Artifact Registry** | Stores Docker images | [deploy.sh#L55](backend/deploy.sh#L55) |
| **Cloud Firestore** | Vector search knowledge base (gemini-embedding-001, 1536-dim COSINE) | [tools.py#L9](backend/fixitbuddy/tools.py#L9) |
| **Gemini API** | gemini-2.5-flash-native-audio-latest for bidi-streaming | [agent.py#L30](backend/fixitbuddy/agent.py#L30) |

---

## Verify It's Live

```bash
# Check service status
gcloud run services describe fixitbuddy-agent --region=us-central1

# Hit the live endpoint
curl -I https://fixitbuddy-agent-hybxqwgczq-uc.a.run.app/
# Expected: HTTP 307 (ADK redirects to its web UI)

# WebSocket endpoint (used by the Android app)
# wss://fixitbuddy-agent-hybxqwgczq-uc.a.run.app/run_live
```

---

## Infrastructure as Code

The entire deployment is scripted in [`backend/deploy.sh`](backend/deploy.sh) — no manual Cloud Console steps required.

```bash
export GOOGLE_CLOUD_PROJECT=your-project-id
export GOOGLE_API_KEY=your-gemini-api-key
cd backend && ./deploy.sh
```

What the script does:
1. Enables Cloud Run, Cloud Build, Artifact Registry, Firestore, and Vertex AI APIs
2. Creates an Artifact Registry Docker repository (idempotent)
3. Builds the container image via Cloud Build
4. Deploys to Cloud Run with session affinity and 1-hour WebSocket timeout
5. Runs a health check against the live URL
