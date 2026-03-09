#!/bin/bash
# FixIt Buddy — Deploy to Cloud Run
# Infrastructure-as-Code deployment script
set -euo pipefail

# Configuration — GOOGLE_CLOUD_PROJECT must be set
PROJECT_ID="${GOOGLE_CLOUD_PROJECT:?Error: GOOGLE_CLOUD_PROJECT env var must be set}"
REGION="${GOOGLE_CLOUD_REGION:-us-central1}"
SERVICE_NAME="fixitbuddy-agent"
AR_REPO="fixitbuddy"

# Validate GOOGLE_API_KEY is set (needed unless using Vertex AI)
if [ -z "${GOOGLE_API_KEY:-}" ]; then
  echo "Warning: GOOGLE_API_KEY is not set. Set it or configure Vertex AI."
fi

echo "╔══════════════════════════════════════════════╗"
echo "║   FixIt Buddy — Cloud Run Deployment         ║"
echo "╚══════════════════════════════════════════════╝"
echo ""
echo "Project:  $PROJECT_ID"
echo "Region:   $REGION"
echo "Service:  $SERVICE_NAME"
echo ""

# Step 1: Enable required APIs
echo "→ Enabling APIs..."
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  firestore.googleapis.com \
  aiplatform.googleapis.com \
  --project=$PROJECT_ID

# Step 2: Create Artifact Registry repo (if not exists)
echo "→ Creating Artifact Registry repository..."
gcloud artifacts repositories describe $AR_REPO \
  --project=$PROJECT_ID \
  --location=$REGION 2>/dev/null || \
gcloud artifacts repositories create $AR_REPO \
  --project=$PROJECT_ID \
  --location=$REGION \
  --repository-format=docker

# Step 3: Build container
IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${AR_REPO}/${SERVICE_NAME}:latest"
echo "→ Building container image..."
gcloud builds submit \
  --project=$PROJECT_ID \
  --region=$REGION \
  --tag $IMAGE

# Step 4: Deploy to Cloud Run
echo "→ Deploying to Cloud Run..."
gcloud run deploy $SERVICE_NAME \
  --project=$PROJECT_ID \
  --image $IMAGE \
  --platform managed \
  --region $REGION \
  --allow-unauthenticated \
  --memory 2Gi \
  --cpu 2 \
  --timeout 3600 \
  --max-instances 10 \
  --session-affinity \
  --set-env-vars="GOOGLE_CLOUD_PROJECT=$PROJECT_ID,GOOGLE_GENAI_USE_VERTEXAI=FALSE,GOOGLE_API_KEY=${GOOGLE_API_KEY},AGENT_MODEL=${AGENT_MODEL:-gemini-2.5-flash-native-audio-latest}"

# Step 5: Get service URL
SERVICE_URL=$(gcloud run services describe $SERVICE_NAME \
  --project=$PROJECT_ID \
  --region=$REGION \
  --format='value(status.url)')

echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║   Deployment Complete!                        ║"
echo "╚══════════════════════════════════════════════╝"
echo ""
echo "Service URL: $SERVICE_URL"
echo ""
echo "Update gradle.properties BACKEND_URL with:"
echo "  BACKEND_URL=$SERVICE_URL"
