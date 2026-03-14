#!/bin/bash
# FixIt Genie — Infrastructure-as-Code deployment script
# Deploys the ADK agent backend to Google Cloud Run.
#
# Usage:
#   export GOOGLE_CLOUD_PROJECT=your-project-id
#   export GOOGLE_API_KEY=your-gemini-api-key
#   ./deploy.sh
#
# Optional env vars:
#   GOOGLE_CLOUD_REGION  — defaults to us-central1
#   AGENT_MODEL          — defaults to gemini-2.5-flash-native-audio-latest
#   DRY_RUN=1            — print plan without deploying
set -euo pipefail

# ── Configuration ────────────────────────────────────────────────────────────
PROJECT_ID="${GOOGLE_CLOUD_PROJECT:?Error: GOOGLE_CLOUD_PROJECT must be set}"
REGION="${GOOGLE_CLOUD_REGION:-us-central1}"
SERVICE_NAME="fixitbuddy-agent"
AR_REPO="fixitbuddy"
AGENT_MODEL="${AGENT_MODEL:-gemini-2.5-flash-native-audio-latest}"
DRY_RUN="${DRY_RUN:-0}"

IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${AR_REPO}/${SERVICE_NAME}:latest"

# ── Banner ───────────────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║   FixIt Genie — Cloud Run Deployment         ║"
echo "╚══════════════════════════════════════════════╝"
echo ""
echo "  Project : $PROJECT_ID"
echo "  Region  : $REGION"
echo "  Service : $SERVICE_NAME"
echo "  Model   : $AGENT_MODEL"
echo "  Image   : $IMAGE"
echo ""

if [ "${DRY_RUN}" = "1" ]; then
  echo "DRY RUN — no changes will be made."
  exit 0
fi

# ── Pre-flight checks ────────────────────────────────────────────────────────
echo "→ Pre-flight checks..."

if ! command -v gcloud &>/dev/null; then
  echo "✗ gcloud CLI not found. Install at https://cloud.google.com/sdk/docs/install"
  exit 1
fi

if ! gcloud auth print-access-token &>/dev/null; then
  echo "✗ Not authenticated. Run: gcloud auth login"
  exit 1
fi

if ! gcloud projects describe "$PROJECT_ID" &>/dev/null; then
  echo "✗ Project '$PROJECT_ID' not found or not accessible."
  exit 1
fi

if [ -z "${GOOGLE_API_KEY:-}" ]; then
  echo "⚠  GOOGLE_API_KEY is not set. Required for Gemini API access."
  echo "   Set it or configure Vertex AI (GOOGLE_GENAI_USE_VERTEXAI=TRUE)."
  read -r -p "   Continue anyway? [y/N] " confirm
  [[ "$confirm" =~ ^[Yy]$ ]] || exit 1
fi

echo "✓ Pre-flight checks passed"
echo ""

# ── Step 1: Enable required GCP APIs ────────────────────────────────────────
echo "→ [1/5] Enabling required GCP APIs..."
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  firestore.googleapis.com \
  aiplatform.googleapis.com \
  --project="$PROJECT_ID"
echo "✓ APIs enabled"
echo ""

# ── Step 2: Create Artifact Registry repository ──────────────────────────────
echo "→ [2/5] Ensuring Artifact Registry repository exists..."
if gcloud artifacts repositories describe "$AR_REPO" \
    --project="$PROJECT_ID" \
    --location="$REGION" &>/dev/null; then
  echo "✓ Repository '$AR_REPO' already exists"
else
  gcloud artifacts repositories create "$AR_REPO" \
    --project="$PROJECT_ID" \
    --location="$REGION" \
    --repository-format=docker
  echo "✓ Repository '$AR_REPO' created"
fi
echo ""

# ── Step 3: Build container image via Cloud Build ───────────────────────────
echo "→ [3/5] Building container image via Cloud Build..."
gcloud builds submit \
  --project="$PROJECT_ID" \
  --region="$REGION" \
  --tag "$IMAGE"
echo "✓ Container image built: $IMAGE"
echo ""

# ── Step 4: Deploy to Cloud Run ──────────────────────────────────────────────
echo "→ [4/5] Deploying to Cloud Run..."
gcloud run deploy "$SERVICE_NAME" \
  --project="$PROJECT_ID" \
  --image "$IMAGE" \
  --platform managed \
  --region "$REGION" \
  --allow-unauthenticated \
  --memory 2Gi \
  --cpu 2 \
  --timeout 3600 \
  --max-instances 10 \
  --session-affinity \
  --set-env-vars="GOOGLE_CLOUD_PROJECT=${PROJECT_ID},GOOGLE_GENAI_USE_VERTEXAI=FALSE,GOOGLE_API_KEY=${GOOGLE_API_KEY:-},AGENT_MODEL=${AGENT_MODEL}"
echo "✓ Service deployed"
echo ""

# ── Step 5: Verify deployment ────────────────────────────────────────────────
echo "→ [5/5] Verifying deployment..."
SERVICE_URL=$(gcloud run services describe "$SERVICE_NAME" \
  --project="$PROJECT_ID" \
  --region="$REGION" \
  --format='value(status.url)')

HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 "${SERVICE_URL}/" || echo "000")

if [ "$HTTP_STATUS" = "200" ] || [ "$HTTP_STATUS" = "404" ]; then
  echo "✓ Service is responding (HTTP $HTTP_STATUS)"
else
  echo "⚠  Service returned HTTP $HTTP_STATUS — it may still be starting up"
fi
echo ""

# ── Done ─────────────────────────────────────────────────────────────────────
echo "╔══════════════════════════════════════════════╗"
echo "║   Deployment Complete!                        ║"
echo "╚══════════════════════════════════════════════╝"
echo ""
echo "  Service URL: $SERVICE_URL"
echo ""
echo "  Update android/gradle.properties:"
echo "    BACKEND_URL=$SERVICE_URL"
echo ""
echo "  Test locally:"
echo "    curl ${SERVICE_URL}/"
echo ""
