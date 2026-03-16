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
| **Current Revision** | fixitbuddy-agent-00034-8z5 |
| **Traffic** | 100% to latest revision |
| **Memory / CPU** | 2 GiB / 2 vCPU |
| **Model** | gemini-2.5-flash-native-audio-latest |

---

## Recent Deployment History

The backend has been redeployed repeatedly during the hackathon build, with each deploy going through `backend/deploy.sh`. Recent revisions:

| Revision | Deployed |
|----------|----------|
| fixitbuddy-agent-00034-8z5 | 2026-03-16 05:41 UTC |
| fixitbuddy-agent-00033-5k7 | 2026-03-16 05:37 UTC |
| fixitbuddy-agent-00032-pkj | 2026-03-16 05:00 UTC |
| fixitbuddy-agent-00031-69s | 2026-03-16 00:42 UTC |
| fixitbuddy-agent-00030-kpg | 2026-03-15 23:48 UTC |
| fixitbuddy-agent-00029-rkq | 2026-03-15 20:57 UTC |
| fixitbuddy-agent-00028-fv2 | 2026-03-15 17:46 UTC |
| fixitbuddy-agent-00027-vxp | 2026-03-15 16:14 UTC |
| fixitbuddy-agent-00026-fqd | 2026-03-15 15:34 UTC |
| fixitbuddy-agent-00025-wdh | 2026-03-15 15:32 UTC |
| fixitbuddy-agent-00024-rwk | 2026-03-15 15:20 UTC |
| fixitbuddy-agent-00023-5sn | 2026-03-15 15:03 UTC |

---

## Google Cloud Services Used

| Service | How It's Used | Code Reference |
|---------|--------------|----------------|
| **Cloud Run** | Hosts the ADK agent with persistent WebSocket + session affinity | [deploy.sh](backend/deploy.sh) |
| **Cloud Build** | Builds the Docker container on deploy | [deploy.sh#L73](backend/deploy.sh#L73) |
| **Artifact Registry** | Stores Docker images | [deploy.sh#L55](backend/deploy.sh#L55) |
| **Cloud Firestore** | Vector search knowledge base (gemini-embedding-001, 1536-dim COSINE) | [tools.py#L9](backend/fixitbuddy/tools.py#L9) |
| **Gemini API** | gemini-2.5-flash-native-audio-latest for live multimodal streaming | [agent.py#L30](backend/fixitbuddy/agent.py#L30) |

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
1. Enables Cloud Run, Cloud Build, Artifact Registry, Firestore, and required Google AI APIs
2. Creates an Artifact Registry Docker repository (idempotent)
3. Builds the container image via Cloud Build
4. Deploys to Cloud Run with session affinity and 1-hour WebSocket timeout
5. Runs a health check against the live URL
