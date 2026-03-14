# FixIt Buddy — AWS Bedrock Backend Implementation Plan

> Parallel backend using Strands Agents + Nova Sonic. All code lives in `backend-aws/`.
> Android app unchanged — switch backends via Settings → Backend URL.
> Full design: `docs/superpowers/specs/2026-03-14-aws-bedrock-backend-design.md`

## Prerequisites

- AWS account with Bedrock access enabled in `us-east-1` (Nova Sonic requirement)
- Nova Sonic model access requested in Bedrock console
- Claude Sonnet 4.5 (`us.anthropic.claude-sonnet-4-5-20251022`) access enabled
- Titan Embeddings v2 access enabled
- AWS CLI configured (`aws configure`)
- Perplexity API key (get at perplexity.ai)
- Docker installed
- Python 3.12+

---

## Step 1 — Scaffold `backend-aws/`

Create the folder structure:

```bash
mkdir -p backend-aws/fixitbuddy_aws
touch backend-aws/fixitbuddy_aws/__init__.py
touch backend-aws/fixitbuddy_aws/config.py
touch backend-aws/fixitbuddy_aws/tools.py
touch backend-aws/fixitbuddy_aws/server.py
touch backend-aws/fixitbuddy_aws/agent.py
touch backend-aws/fixitbuddy_aws/vision.py
touch backend-aws/seed_knowledge_aws.py
touch backend-aws/Dockerfile
touch backend-aws/deploy-aws.sh
touch backend-aws/requirements.txt
touch backend-aws/.env.example
```

---

## Step 2 — `requirements.txt`

```
strands-agents[bidi]~=0.1.0
boto3~=1.38.0
fastapi~=0.116.0
uvicorn[standard]~=0.35.0
websockets~=14.0
httpx~=0.28.0
youtube-transcript-api~=1.0.0
pypdf~=5.0.0
python-dotenv~=1.0.0
```

---

## Step 3 — `fixitbuddy_aws/config.py`

```python
"""FixIt Buddy AWS — Configuration."""
import os
from dotenv import load_dotenv

load_dotenv()

AWS_REGION: str = os.environ.get("AWS_REGION", "us-east-1")
BEDROCK_KB_ID: str = os.environ.get("BEDROCK_KB_ID", "")
PERPLEXITY_API_KEY: str = os.environ.get("PERPLEXITY_API_KEY", "")
NOVA_SONIC_MODEL: str = "amazon.nova-sonic-v1:0"
CLAUDE_VISION_MODEL: str = "us.anthropic.claude-sonnet-4-5-20251022"
NOVA_VOICE: str = os.environ.get("NOVA_VOICE", "matthew")
PORT: int = int(os.environ.get("PORT", "8080"))
```

---

## Step 4 — `fixitbuddy_aws/vision.py`

Calls Claude Sonnet 4.5 on Bedrock to describe a JPEG camera frame.

```python
"""Vision sidechannel — Claude Sonnet 4.5 on Bedrock."""
import boto3
import json
from .config import AWS_REGION, CLAUDE_VISION_MODEL


def analyze_frame(jpeg_b64: str) -> str:
    """Describe a camera frame for equipment repair context."""
    client = boto3.client("bedrock-runtime", region_name=AWS_REGION)
    body = {
        "anthropic_version": "bedrock-2023-05-31",
        "max_tokens": 300,
        "messages": [{
            "role": "user",
            "content": [
                {
                    "type": "image",
                    "source": {
                        "type": "base64",
                        "media_type": "image/jpeg",
                        "data": jpeg_b64
                    }
                },
                {
                    "type": "text",
                    "text": (
                        "You are helping repair equipment. Describe what you see in the image "
                        "in 1-2 sentences: what equipment is visible, its condition, any error "
                        "codes or displays, and anything that looks broken or unusual."
                    )
                }
            ]
        }]
    }
    response = client.invoke_model(
        modelId=CLAUDE_VISION_MODEL,
        body=json.dumps(body)
    )
    result = json.loads(response["body"].read())
    return result["content"][0]["text"]
```

---

## Step 5 — `fixitbuddy_aws/tools.py`

Copy base content from `backend/fixitbuddy/tools.py`, then make these changes:

### What to copy verbatim
- The entire `_KNOWLEDGE_BASE` dict (all 8 equipment entries)
- The entire `get_safety_warnings()` function
- The `analyze_youtube_repair_video()` function (primary `YouTubeTranscriptApi` logic only)
- The `log_diagnostic_step()` function signature

### What to replace

**`lookup_equipment_knowledge`** — replace Firestore with Bedrock KB:
```python
import boto3
from strands import tool
from .config import AWS_REGION, BEDROCK_KB_ID

@tool
def lookup_equipment_knowledge(
    query: str, category: str = "", error_code: str = ""
) -> dict:
    """Look up equipment knowledge using semantic search."""
    search_query = f"{query} {error_code} {category}".strip()

    # Primary: Bedrock Knowledge Base
    if BEDROCK_KB_ID:
        try:
            client = boto3.client("bedrock-agent-runtime", region_name=AWS_REGION)
            response = client.retrieve(
                knowledgeBaseId=BEDROCK_KB_ID,
                retrievalQuery={"text": search_query},
                retrievalConfiguration={"vectorSearchConfiguration": {"numberOfResults": 3}}
            )
            results = [
                {"content": r["content"]["text"], "score": r["score"]}
                for r in response.get("retrievalResults", [])
            ]
            if results:
                return {"found": True, "results": results}
        except Exception as e:
            print(f"Bedrock KB error: {e}")

    # Fallback: embedded KB keyword search
    query_lower = search_query.lower()
    matches = []
    for doc_id, doc in _KNOWLEDGE_BASE.items():
        keywords = doc.get("keywords", [])
        if any(k in query_lower for k in keywords):
            matches.append({"content": str(doc), "score": 0.5})
    if matches:
        return {"found": True, "results": matches[:3]}
    return {"found": False, "message": "No matching equipment knowledge found"}
```

**`analyze_youtube_repair_video`** — replace Gemini summarization with Claude:
```python
# After getting transcript text, summarize with Claude instead of Gemini:
import boto3, json
from .config import AWS_REGION, CLAUDE_VISION_MODEL

def _summarize_with_claude(transcript: str, question: str) -> str:
    client = boto3.client("bedrock-runtime", region_name=AWS_REGION)
    body = {
        "anthropic_version": "bedrock-2023-05-31",
        "max_tokens": 1024,
        "messages": [{"role": "user", "content":
            f"Extract step-by-step repair instructions from this transcript.\n\n{transcript[:8000]}"
        }]
    }
    response = client.invoke_model(modelId=CLAUDE_VISION_MODEL, body=json.dumps(body))
    return json.loads(response["body"].read())["content"][0]["text"]
```

**`lookup_user_manual`** — replace Gemini grounded search with Perplexity:
```python
import httpx
from .config import PERPLEXITY_API_KEY

@tool
def lookup_user_manual(brand: str, model_number: str) -> dict:
    """Fetch and summarize the user manual for a specific appliance/equipment model."""
    if not PERPLEXITY_API_KEY:
        return {"found": False, "message": "Perplexity API key not configured"}

    # Step 1: Find PDF URL via Perplexity
    try:
        r = httpx.post(
            "https://api.perplexity.ai/chat/completions",
            headers={"Authorization": f"Bearer {PERPLEXITY_API_KEY}"},
            json={
                "model": "sonar-pro",
                "messages": [{
                    "role": "user",
                    "content": (
                        f"Find the official PDF user manual for {brand} model {model_number}. "
                        "Return ONLY the direct URL to the PDF file, nothing else."
                    )
                }]
            },
            timeout=15.0
        )
        pdf_url = r.json()["choices"][0]["message"]["content"].strip()
    except Exception as e:
        return {"found": False, "message": f"Search failed: {e}"}

    # Step 2: Fetch and parse PDF (same as existing backend)
    try:
        import io
        import requests
        from pypdf import PdfReader

        resp = requests.get(pdf_url, timeout=30, headers={"User-Agent": "Mozilla/5.0"})
        reader = PdfReader(io.BytesIO(resp.content))
        text = " ".join(page.extract_text() or "" for page in reader.pages[:20])
        return {"found": True, "brand": brand, "model": model_number,
                "content": text[:4000], "url": pdf_url}
    except Exception as e:
        return {"found": False, "message": f"PDF parse failed: {e}", "url": pdf_url}
```

**`log_diagnostic_step`** — stdout only:
```python
@tool
def log_diagnostic_step(
    step_number: int, description: str, observation: str = "", result: str = ""
) -> dict:
    """Record a diagnostic step in the session log."""
    step = {"step": step_number, "description": description,
            "observation": observation, "result": result}
    print(f"[DIAGNOSTIC] {json.dumps(step)}")  # CloudWatch via App Runner stdout
    return {"logged": True, "step": step}
```

---

## Step 6 — `fixitbuddy_aws/agent.py`

```python
"""FixIt Buddy AWS — Strands BidiAgent definition."""
import pathlib
from strands.experimental.bidi import BidiAgent
from strands.experimental.bidi.models import BidiNovaSonicModel
from .config import AWS_REGION, NOVA_SONIC_MODEL, NOVA_VOICE
from .tools import (
    lookup_equipment_knowledge,
    get_safety_warnings,
    log_diagnostic_step,
    analyze_youtube_repair_video,
    lookup_user_manual,
)

# Load skills markdown into system prompt (replaces ADK SkillToolset)
_SKILLS_DIR = pathlib.Path(__file__).parent.parent.parent / "backend" / "fixitbuddy" / "skills"

def _load_skills_text() -> str:
    if not _SKILLS_DIR.exists():
        return ""
    parts = []
    for skill_dir in sorted(_SKILLS_DIR.iterdir()):
        if not skill_dir.is_dir():
            continue
        skill_md = skill_dir / "SKILL.md"
        if skill_md.exists():
            parts.append(f"## {skill_dir.name.upper()} SKILL\n\n{skill_md.read_text()}")
        refs = skill_dir / "references"
        if refs.exists():
            for ref in sorted(refs.glob("*.md")):
                parts.append(f"### {ref.stem}\n\n{ref.read_text()}")
    return "\n\n".join(parts)


# Copy SYSTEM_INSTRUCTION verbatim from backend/fixitbuddy/agent.py
# then append skills content
SYSTEM_INSTRUCTION = """You are FixIt Genie, an expert equipment diagnosis and repair assistant.
You can see through the user's camera and hear them describe problems.
[... copy full SYSTEM_INSTRUCTION from backend/fixitbuddy/agent.py ...]
"""

_skills_content = _load_skills_text()
if _skills_content:
    SYSTEM_INSTRUCTION += f"\n\n## DOMAIN KNOWLEDGE\n\n{_skills_content}"


_model = BidiNovaSonicModel(
    model_id=NOVA_SONIC_MODEL,
    provider_config={
        "audio": {
            "input_sample_rate": 16000,
            "output_sample_rate": 24000,
            "voice": NOVA_VOICE,
        }
    },
    client_config={"region": AWS_REGION},
)


def create_agent() -> BidiAgent:
    return BidiAgent(
        model=_model,
        tools=[
            lookup_equipment_knowledge,
            get_safety_warnings,
            log_diagnostic_step,
            analyze_youtube_repair_video,
            lookup_user_manual,
        ],
        system_prompt=SYSTEM_INSTRUCTION,
    )
```

---

## Step 7 — `fixitbuddy_aws/server.py`

FastAPI server matching the ADK WebSocket protocol Android already speaks.

```python
"""FixIt Buddy AWS — FastAPI WebSocket server (ADK-compatible protocol)."""
import asyncio
import base64
import json
import time
import uuid
from typing import Any

import uvicorn
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware

from .agent import create_agent
from .config import PORT
from .vision import analyze_frame

app = FastAPI(title="FixIt Buddy AWS")
app.add_middleware(CORSMiddleware, allow_origins=["*"],
                   allow_methods=["*"], allow_headers=["*"])

_sessions: dict[str, dict] = {}


@app.get("/health")
async def health() -> dict:
    return {"status": "ok"}


@app.post("/apps/{app_name}/users/{user_id}/sessions")
async def create_session(app_name: str, user_id: str) -> dict:
    """Create a new session — same endpoint Android uses for Google ADK."""
    session_id = str(uuid.uuid4())
    _sessions[session_id] = {"user_id": user_id, "created_at": time.time()}
    return {"id": session_id}


@app.websocket("/run_live")
async def run_live(
    ws: WebSocket,
    app_name: str = "fixitbuddy",
    user_id: str = "user",
    session_id: str = "",
    modalities: str = "AUDIO",
):
    """Bidi-streaming WebSocket — same URL pattern as ADK /run_live."""
    await ws.accept()
    agent = create_agent()
    audio_queue: asyncio.Queue[bytes] = asyncio.Queue()
    vision_queue: asyncio.Queue[str] = asyncio.Queue()

    async def receive_messages():
        """Receive from Android: binary=PCM audio, JSON=video frame or text."""
        try:
            while True:
                msg = await ws.receive()
                if "bytes" in msg:
                    # Raw PCM audio
                    await audio_queue.put(msg["bytes"])
                elif "text" in msg:
                    data = json.loads(msg["text"])
                    blob = data.get("blob", {})
                    mime = blob.get("mime_type", "")
                    if mime.startswith("image/jpeg"):
                        # Video frame — analyze in background
                        asyncio.create_task(_handle_frame(blob["data"]))
                    elif mime.startswith("audio/pcm"):
                        raw = base64.b64decode(blob["data"])
                        await audio_queue.put(raw)
        except WebSocketDisconnect:
            pass

    async def _handle_frame(jpeg_b64: str):
        try:
            description = await asyncio.to_thread(analyze_frame, jpeg_b64)
            await vision_queue.put(description)
        except Exception as e:
            print(f"Vision error: {e}")

    async def audio_input_stream():
        """Feed PCM audio + vision descriptions to BidiAgent."""
        while True:
            # Drain vision descriptions first (inject as context)
            while not vision_queue.empty():
                desc = await vision_queue.get()
                # Yield as a text input event so Nova Sonic knows what the camera sees
                yield {"text": f"[Camera view]: {desc}"}
            # Then yield audio
            try:
                chunk = await asyncio.wait_for(audio_queue.get(), timeout=0.1)
                yield {"blob": {"mime_type": "audio/pcm;rate=16000", "data": chunk}}
            except asyncio.TimeoutError:
                continue

    async def send_output(event: Any):
        """Forward BidiAgent output to Android."""
        try:
            if hasattr(event, "audio") and event.audio:
                await ws.send_bytes(event.audio)
            elif hasattr(event, "text") and event.text:
                await ws.send_text(json.dumps({
                    "content": {"parts": [{"text": event.text}]}
                }))
        except Exception:
            pass

    receiver_task = asyncio.create_task(receive_messages())
    try:
        await agent.run(
            inputs=[audio_input_stream()],
            outputs=[send_output],
        )
    finally:
        receiver_task.cancel()


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=PORT)
```

---

## Step 8 — `Dockerfile`

```dockerfile
FROM python:3.12-slim

RUN useradd -m -u 1000 appuser

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .
RUN chown -R appuser:appuser /app

USER appuser

ENV PORT=8080
EXPOSE 8080

CMD ["uvicorn", "fixitbuddy_aws.server:app", "--host", "0.0.0.0", "--port", "8080"]
```

---

## Step 9 — `deploy-aws.sh`

```bash
#!/bin/bash
set -e

# ─── Required ────────────────────────────────────────────────────────────────
: "${AWS_ACCOUNT_ID:?Set AWS_ACCOUNT_ID}"
: "${PERPLEXITY_API_KEY:?Set PERPLEXITY_API_KEY}"

# ─── Optional ────────────────────────────────────────────────────────────────
REGION="${AWS_REGION:-us-east-1}"
BEDROCK_KB_ID="${BEDROCK_KB_ID:-}"
NOVA_VOICE="${NOVA_VOICE:-matthew}"
SERVICE_NAME="fixitbuddy-aws"
ECR_URL="${AWS_ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
IMAGE="${ECR_URL}/${SERVICE_NAME}:latest"

echo "==> [1/5] Create ECR repo (idempotent)"
aws ecr create-repository --repository-name "${SERVICE_NAME}" --region "${REGION}" 2>/dev/null || true

echo "==> [2/5] Docker login to ECR"
aws ecr get-login-password --region "${REGION}" | \
  docker login --username AWS --password-stdin "${ECR_URL}"

echo "==> [3/5] Build + push Docker image"
docker build -t "${SERVICE_NAME}" .
docker tag "${SERVICE_NAME}:latest" "${IMAGE}"
docker push "${IMAGE}"

echo "==> [4/5] Deploy to App Runner"
ENV_VARS="AWS_REGION=${REGION},PERPLEXITY_API_KEY=${PERPLEXITY_API_KEY},BEDROCK_KB_ID=${BEDROCK_KB_ID},NOVA_VOICE=${NOVA_VOICE}"

# Get existing service ARN (empty if first deploy)
SERVICE_ARN=$(aws apprunner list-services --region "${REGION}" \
  --query "ServiceSummaryList[?ServiceName=='${SERVICE_NAME}'].ServiceArn" \
  --output text 2>/dev/null)

SOURCE_CONFIG=$(cat <<EOF
{
  "ImageRepository": {
    "ImageIdentifier": "${IMAGE}",
    "ImageRepositoryType": "ECR",
    "ImageConfiguration": {
      "Port": "8080",
      "RuntimeEnvironmentVariables": {
        "AWS_REGION": "${REGION}",
        "PERPLEXITY_API_KEY": "${PERPLEXITY_API_KEY}",
        "BEDROCK_KB_ID": "${BEDROCK_KB_ID}",
        "NOVA_VOICE": "${NOVA_VOICE}"
      }
    }
  },
  "AutoDeploymentsEnabled": false
}
EOF
)

if [ -z "${SERVICE_ARN}" ]; then
  echo "    Creating new App Runner service..."
  SERVICE_ARN=$(aws apprunner create-service \
    --service-name "${SERVICE_NAME}" \
    --source-configuration "${SOURCE_CONFIG}" \
    --instance-configuration "Cpu=1024,Memory=2048" \
    --region "${REGION}" \
    --query "Service.ServiceArn" --output text)
else
  echo "    Updating existing App Runner service..."
  aws apprunner update-service \
    --service-arn "${SERVICE_ARN}" \
    --source-configuration "${SOURCE_CONFIG}" \
    --region "${REGION}" > /dev/null
fi

echo "==> [5/5] Waiting for service to be running..."
aws apprunner wait service-running --service-arn "${SERVICE_ARN}" --region "${REGION}" 2>/dev/null || true

SERVICE_URL=$(aws apprunner describe-service \
  --service-arn "${SERVICE_ARN}" --region "${REGION}" \
  --query "Service.ServiceUrl" --output text)

echo ""
echo "✓ Deployed! Service URL:"
echo "  https://${SERVICE_URL}"
echo ""
echo "→ In Android app: Settings → Backend URL → wss://${SERVICE_URL}"
```

---

## Step 10 — `seed_knowledge_aws.py`

Seeds S3 + creates Bedrock Knowledge Base from the existing skill markdown files.

```python
#!/usr/bin/env python3
"""Seed Bedrock Knowledge Base from existing skill markdown files."""
import json
import pathlib
import sys
import time

import boto3

REGION = "us-east-1"
SKILLS_DIR = pathlib.Path(__file__).parent.parent / "backend" / "fixitbuddy" / "skills"

# Import embedded KB from existing backend
sys.path.insert(0, str(pathlib.Path(__file__).parent.parent / "backend"))
try:
    from fixitbuddy.tools import _KNOWLEDGE_BASE
except ImportError:
    _KNOWLEDGE_BASE = {}


def get_account_id() -> str:
    return boto3.client("sts", region_name=REGION).get_caller_identity()["Account"]


def create_s3_bucket(account_id: str) -> str:
    bucket = f"fixitbuddy-knowledge-{account_id}"
    s3 = boto3.client("s3", region_name=REGION)
    try:
        s3.create_bucket(Bucket=bucket,
                         CreateBucketConfiguration={"LocationConstraint": REGION})
        print(f"  Created bucket: {bucket}")
    except s3.exceptions.BucketAlreadyOwnedByYou:
        print(f"  Bucket exists: {bucket}")
    return bucket


def upload_documents(bucket: str) -> None:
    s3 = boto3.client("s3", region_name=REGION)

    # Upload skill markdown files
    for md_file in SKILLS_DIR.rglob("*.md"):
        key = f"skills/{md_file.relative_to(SKILLS_DIR)}"
        s3.upload_file(str(md_file), bucket, key)
        print(f"  Uploaded: {key}")

    # Upload embedded KB entries as text files
    for doc_id, doc in _KNOWLEDGE_BASE.items():
        text = json.dumps(doc, indent=2)
        s3.put_object(Bucket=bucket, Key=f"equipment/{doc_id}.txt",
                      Body=text.encode(), ContentType="text/plain")
        print(f"  Uploaded: equipment/{doc_id}.txt")


def create_knowledge_base(bucket: str) -> str:
    bedrock = boto3.client("bedrock-agent", region_name=REGION)

    # Create KB
    print("  Creating Knowledge Base (OpenSearch Serverless)...")
    kb = bedrock.create_knowledge_base(
        name="fixitbuddy-equipment-kb",
        description="FixIt Buddy equipment repair knowledge",
        roleArn=f"arn:aws:iam::{get_account_id()}:role/AmazonBedrockExecutionRoleForKnowledgeBase",
        knowledgeBaseConfiguration={
            "type": "VECTOR",
            "vectorKnowledgeBaseConfiguration": {
                "embeddingModelArn": f"arn:aws:bedrock:{REGION}::foundation-model/amazon.titan-embed-text-v2:0"
            }
        },
        storageConfiguration={
            "type": "OPENSEARCH_SERVERLESS",
            "opensearchServerlessConfiguration": {
                "collectionArn": "",  # Bedrock creates this automatically
                "vectorIndexName": "fixitbuddy-index",
                "fieldMapping": {
                    "vectorField": "embedding",
                    "textField": "text",
                    "metadataField": "metadata"
                }
            }
        }
    )
    kb_id = kb["knowledgeBase"]["knowledgeBaseId"]
    print(f"  Knowledge Base ID: {kb_id}")

    # Add S3 data source
    bedrock.create_data_source(
        knowledgeBaseId=kb_id,
        name="fixitbuddy-s3-source",
        dataSourceConfiguration={
            "type": "S3",
            "s3Configuration": {"bucketArn": f"arn:aws:s3:::{bucket}"}
        }
    )

    # Start ingestion
    print("  Starting ingestion job...")
    time.sleep(5)
    bedrock.start_ingestion_job(knowledgeBaseId=kb_id,
                                dataSourceId=bedrock.list_data_sources(
                                    knowledgeBaseId=kb_id)["dataSourceSummaries"][0]["dataSourceId"])
    return kb_id


if __name__ == "__main__":
    print("==> Seeding Bedrock Knowledge Base")
    account_id = get_account_id()
    bucket = create_s3_bucket(account_id)
    print("==> Uploading documents to S3")
    upload_documents(bucket)
    print("==> Creating Bedrock KB")
    kb_id = create_knowledge_base(bucket)
    print(f"\n✓ Done! Set this env var in App Runner:\n  BEDROCK_KB_ID={kb_id}")
```

---

## Step 11 — `.env.example`

```bash
# AWS credentials (not needed in App Runner — uses IAM role)
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
AWS_REGION=us-east-1

# Perplexity (required for web search + manual lookup)
PERPLEXITY_API_KEY=pplx-...

# Bedrock Knowledge Base (optional — app falls back to embedded KB)
BEDROCK_KB_ID=

# Nova Sonic voice
NOVA_VOICE=matthew

# Server
PORT=8080
```

---

## IAM Setup

### App Runner task role policy

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "bedrock:InvokeModel",
        "bedrock-runtime:InvokeModelWithBidirectionalStream"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": "bedrock-agent-runtime:Retrieve",
      "Resource": "*"
    }
  ]
}
```

Attach to the App Runner instance role, or run locally with a profile that has these permissions.

---

## Local Development

```bash
cd backend-aws

# Install deps
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# Set env vars
cp .env.example .env
# fill in PERPLEXITY_API_KEY, AWS credentials

# Run
uvicorn fixitbuddy_aws.server:app --port 8080 --reload

# Test session creation
curl -X POST http://localhost:8080/apps/fixitbuddy/users/test/sessions
# → {"id": "<uuid>"}

# Test WebSocket (install wscat: npm i -g wscat)
wscat -c "ws://localhost:8080/run_live?app_name=fixitbuddy&user_id=test&session_id=<uuid>"
```

---

## Deploy

```bash
cd backend-aws

# Optional: seed knowledge base first
python seed_knowledge_aws.py
# → copy the BEDROCK_KB_ID output

# Deploy
AWS_ACCOUNT_ID=123456789012 \
PERPLEXITY_API_KEY=pplx-xxx \
BEDROCK_KB_ID=abc123 \
./deploy-aws.sh

# → outputs: wss://<app-runner-url>
# → paste into Android Settings → Backend URL
```

---

## Notes

- Nova Sonic only available in `us-east-1`, `eu-north-1`, `ap-northeast-1` — default is `us-east-1`
- `strands-agents[bidi]` requires Python 3.12+
- App Runner auto-provisions HTTPS → Android connects via `wss://` (not `ws://`)
- Bedrock KB is optional — embedded `_KNOWLEDGE_BASE` fallback works out of the box
- Skills markdown is loaded from `backend/fixitbuddy/skills/` at startup (shared read-only)
