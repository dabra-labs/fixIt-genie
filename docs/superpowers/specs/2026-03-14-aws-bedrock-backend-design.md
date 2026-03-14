# FixIt Buddy — AWS Bedrock Parallel Backend Design

**Date:** 2026-03-14
**Status:** Approved

## Context

FixIt Buddy currently runs on Google Cloud (ADK + Gemini Live). This adds a parallel AWS backend
(`backend-aws/`) using Strands Agents + Nova Sonic for real-time bidi audio streaming. The Android
app already has a configurable backend URL in Settings — no Android changes needed. The user switches
backends by changing the URL in Settings.

**Goal:** Keep it simple. No CDK, App Runner for hosting, duplicate all code (no sharing with `backend/`).

---

## Architecture

```
Android App (unchanged)
    ↓ wss:// same ADK-compatible protocol
FastAPI + BidiAgent (AWS App Runner, HTTPS auto-provisioned)
    ├── BidiNovaSonicModel            → Nova Sonic (Bedrock, us-east-1)
    ├── Vision sidechannel            → Claude Sonnet 4.5 (Bedrock) per JPEG frame
    ├── @tool lookup_equipment_knowledge → Bedrock KB (S3 + OpenSearch Serverless)
    ├── @tool get_safety_warnings        → embedded safety matrix (copied)
    ├── @tool analyze_youtube_repair_video → youtube-transcript-api + Claude summary
    ├── @tool lookup_user_manual         → Perplexity sonar-pro API
    └── @tool log_diagnostic_step        → stdout → CloudWatch (automatic in App Runner)
```

---

## Folder Structure

```
backend-aws/
├── fixitbuddy_aws/
│   ├── __init__.py
│   ├── agent.py        — BidiAgent + system prompt (skills markdown embedded in prompt)
│   ├── tools.py        — 5 @tool functions + embedded KB fallback dict
│   ├── server.py       — FastAPI WebSocket server (ADK-compatible protocol)
│   ├── vision.py       — Claude Sonnet 4.5 vision sidechannel helper
│   └── config.py       — env config
├── seed_knowledge_aws.py — uploads markdown refs to S3, creates Bedrock KB
├── Dockerfile            — python:3.12-slim, uvicorn
├── deploy-aws.sh         — ECR build+push + App Runner create/update (AWS CLI only)
├── requirements.txt
└── .env.example
```

---

## Files

### `requirements.txt`

```
strands-agents[bidi]~=0.1.0
boto3~=1.38.0
fastapi~=0.116.0
uvicorn[standard]~=0.35.0
websockets~=14.0
httpx~=0.28.0
youtube-transcript-api~=1.0.0
pypdf~=5.0.0
```

---

### `config.py`

```python
import os

AWS_REGION = os.environ.get("AWS_REGION", "us-east-1")
BEDROCK_KB_ID = os.environ.get("BEDROCK_KB_ID", "")
PERPLEXITY_API_KEY = os.environ.get("PERPLEXITY_API_KEY", "")
NOVA_SONIC_MODEL = "amazon.nova-sonic-v1:0"
CLAUDE_VISION_MODEL = "us.anthropic.claude-sonnet-4-5-20251022"
NOVA_VOICE = os.environ.get("NOVA_VOICE", "matthew")
PORT = int(os.environ.get("PORT", "8080"))
```

---

### `tools.py` — 5 `@tool` functions

**`lookup_equipment_knowledge(query, category="", error_code="")`**
- Primary: `boto3 bedrock-agent-runtime.retrieve()` against `BEDROCK_KB_ID`
- Fallback: keyword search against copied `_KNOWLEDGE_BASE` dict (from `backend/fixitbuddy/tools.py`)
- Returns: `{"found": bool, "results": [...]}`

**`get_safety_warnings(action_type, equipment_category="")`**
- Copy verbatim from `backend/fixitbuddy/tools.py` — no changes needed

**`analyze_youtube_repair_video(youtube_url, question="")`**
- Copy `YouTubeTranscriptApi` logic from `backend/fixitbuddy/tools.py`
- Replace Gemini summarization call with Claude Sonnet 4.5 via `boto3 bedrock-runtime.invoke_model`

**`lookup_user_manual(brand, model_number)`**
- Call Perplexity sonar-pro to find PDF URL:
  ```python
  httpx.post("https://api.perplexity.ai/chat/completions",
      headers={"Authorization": f"Bearer {PERPLEXITY_API_KEY}"},
      json={"model": "sonar-pro", "messages": [{"role": "user",
          "content": f"Find official PDF manual URL for {brand} {model_number}"}]})
  ```
- Fetch + parse PDF with `pypdf` (same pattern as existing tool)

**`log_diagnostic_step(step_number, description, observation="", result="")`**
- `print(json.dumps({...}))` — App Runner streams stdout to CloudWatch automatically
- Returns `{"logged": True, "step": {...}}`

---

### `vision.py` — Vision sidechannel

```python
import boto3, json
from .config import AWS_REGION, CLAUDE_VISION_MODEL

async def analyze_frame(jpeg_b64: str) -> str:
    client = boto3.client("bedrock-runtime", region_name=AWS_REGION)
    body = {
        "anthropic_version": "bedrock-2023-05-31",
        "max_tokens": 300,
        "messages": [{"role": "user", "content": [
            {"type": "image", "source": {"type": "base64", "media_type": "image/jpeg", "data": jpeg_b64}},
            {"type": "text", "text": "Describe what you see in one sentence for equipment repair context."}
        ]}]
    }
    response = client.invoke_model(modelId=CLAUDE_VISION_MODEL, body=json.dumps(body))
    return json.loads(response["body"].read())["content"][0]["text"]
```

---

### `agent.py`

```python
import pathlib
from strands.experimental.bidi import BidiAgent
from strands.experimental.bidi.models import BidiNovaSonicModel
from .config import AWS_REGION, NOVA_SONIC_MODEL, NOVA_VOICE
from .tools import (lookup_equipment_knowledge, get_safety_warnings,
                    log_diagnostic_step, analyze_youtube_repair_video, lookup_user_manual)

# Embed all skills markdown into system prompt at startup (replaces ADK SkillToolset)
_SKILLS_DIR = pathlib.Path(__file__).parent.parent.parent / "backend" / "fixitbuddy" / "skills"

def _load_skills_text() -> str:
    parts = []
    for skill_dir in sorted(_SKILLS_DIR.iterdir()):
        if skill_dir.is_dir():
            skill_md = skill_dir / "SKILL.md"
            if skill_md.exists():
                parts.append(skill_md.read_text())
            refs = skill_dir / "references"
            if refs.exists():
                for ref in sorted(refs.glob("*.md")):
                    parts.append(ref.read_text())
    return "\n\n".join(parts)

SYSTEM_INSTRUCTION = """...(same as backend/fixitbuddy/agent.py SYSTEM_INSTRUCTION)..."""
SYSTEM_INSTRUCTION += "\n\n## DOMAIN KNOWLEDGE\n\n" + _load_skills_text()

_model = BidiNovaSonicModel(
    model_id=NOVA_SONIC_MODEL,
    provider_config={"audio": {"input_sample_rate": 16000, "output_sample_rate": 24000, "voice": NOVA_VOICE}},
    client_config={"region": AWS_REGION},
)

def create_agent() -> BidiAgent:
    return BidiAgent(
        model=_model,
        tools=[lookup_equipment_knowledge, get_safety_warnings,
               log_diagnostic_step, analyze_youtube_repair_video, lookup_user_manual],
        system_prompt=SYSTEM_INSTRUCTION,
    )
```

---

### `server.py` — FastAPI WebSocket (ADK-compatible protocol)

Two endpoints Android already knows:

```python
@app.post("/apps/fixitbuddy/users/{user_id}/sessions")
async def create_session(user_id: str):
    session_id = str(uuid.uuid4())
    _sessions[session_id] = {"user_id": user_id}
    return {"id": session_id}

@app.websocket("/run_live")
async def run_live(ws: WebSocket, app_name: str, user_id: str, session_id: str, modalities: str = "AUDIO"):
    await ws.accept()
    agent = create_agent()

    async def receive_from_client():
        async for msg in ws.iter_bytes():   # binary = raw PCM audio
            yield msg
        # JSON messages handled separately in a background task:
        # image/jpeg blobs → analyze_frame() → inject description as text input

    async def send_to_client(event):
        if is_audio(event):
            await ws.send_bytes(pcm_data)
        else:
            await ws.send_json({"content": {"parts": [{"text": event.text}]}})

    await agent.run(inputs=[receive_from_client()], outputs=[send_to_client])
```

**Vision injection:** A background task receives JSON messages on the WebSocket. When `mime_type == "image/jpeg"`, calls `vision.analyze_frame()` and injects the description as a text message into the Nova Sonic input stream.

---

### `Dockerfile`

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

### `deploy-aws.sh`

```bash
#!/bin/bash
set -e
# Required env vars: AWS_ACCOUNT_ID, PERPLEXITY_API_KEY
# Optional: AWS_REGION (default us-east-1), BEDROCK_KB_ID, NOVA_VOICE

REGION=${AWS_REGION:-us-east-1}
ECR_URL="${AWS_ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
IMAGE="${ECR_URL}/fixitbuddy-aws:latest"

echo "==> Creating ECR repo (idempotent)"
aws ecr create-repository --repository-name fixitbuddy-aws --region $REGION 2>/dev/null || true

echo "==> Docker login to ECR"
aws ecr get-login-password --region $REGION | \
  docker login --username AWS --password-stdin $ECR_URL

echo "==> Build + push"
docker build -t fixitbuddy-aws .
docker tag fixitbuddy-aws:latest $IMAGE
docker push $IMAGE

echo "==> Deploy to App Runner"
aws apprunner create-service \
  --service-name fixitbuddy-aws \
  --source-configuration "{
    \"ImageRepository\": {
      \"ImageIdentifier\": \"$IMAGE\",
      \"ImageRepositoryType\": \"ECR\",
      \"ImageConfiguration\": {
        \"Port\": \"8080\",
        \"RuntimeEnvironmentVariables\": {
          \"AWS_REGION\": \"$REGION\",
          \"PERPLEXITY_API_KEY\": \"$PERPLEXITY_API_KEY\",
          \"BEDROCK_KB_ID\": \"${BEDROCK_KB_ID:-}\",
          \"NOVA_VOICE\": \"${NOVA_VOICE:-matthew}\"
        }
      }
    },
    \"AutoDeploymentsEnabled\": false
  }" \
  --instance-configuration "Cpu=1024,Memory=2048" \
  --region $REGION 2>/dev/null || \
aws apprunner update-service \
  --service-arn "$(aws apprunner list-services --region $REGION \
    --query 'ServiceSummaryList[?ServiceName==`fixitbuddy-aws`].ServiceArn' \
    --output text)" \
  --source-configuration "{...}" \
  --region $REGION

echo ""
echo "==> Done! Get your service URL:"
echo "aws apprunner describe-service --service-arn <arn> --query 'Service.ServiceUrl'"
echo "Update Settings → Backend URL in Android app to: wss://<service-url>"
```

---

### `seed_knowledge_aws.py`

```python
# Steps:
# 1. Create S3 bucket: fixitbuddy-knowledge-{aws_account_id}
# 2. Upload backend/fixitbuddy/skills/**/*.md → s3://bucket/skills/
# 3. Export _KNOWLEDGE_BASE items as .txt → s3://bucket/equipment/
# 4. Create Bedrock KB (bedrock-agent client):
#    - embeddingModelArn: amazon.titan-embed-text-v2:0
#    - storageConfiguration: OPENSEARCH_SERVERLESS (managed, auto-created)
# 5. Add S3 data source, start sync job
# 6. Print KB ID → export BEDROCK_KB_ID=<id> and set in App Runner env vars
```

---

### `.env.example`

```bash
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=          # not needed in App Runner (uses IAM role)
AWS_SECRET_ACCESS_KEY=      # not needed in App Runner (uses IAM role)
PERPLEXITY_API_KEY=
BEDROCK_KB_ID=              # set after running seed_knowledge_aws.py
NOVA_VOICE=matthew
PORT=8080
```

---

## IAM Permissions (App Runner task role)

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {"Effect": "Allow", "Action": ["bedrock:InvokeModel",
      "bedrock-runtime:InvokeModelWithBidirectionalStream"],
      "Resource": "*"},
    {"Effect": "Allow", "Action": "bedrock-agent-runtime:Retrieve",
      "Resource": "*"}
  ]
}
```

---

## Simplicity Decisions

| Decision | Choice | Reason |
|---|---|---|
| IaC | AWS CLI only (no CDK) | CDK is overkill for a hackathon |
| Session logs | `print()` → CloudWatch | No DynamoDB setup needed |
| KB fallback | Embedded `_KNOWLEDGE_BASE` dict | App works with zero AWS setup |
| Skills | Markdown embedded in system prompt | No ADK SkillToolset dependency |
| Code sharing | Everything duplicated | Simple to reason about; share later if needed |
| Deployment | App Runner | Closest to Cloud Run; auto HTTPS/WSS |

---

## Verification

```bash
# 1. Local test (needs AWS creds)
cd backend-aws
pip install -r requirements.txt
uvicorn fixitbuddy_aws.server:app --port 8080

# 2. Create session
curl -X POST http://localhost:8080/apps/fixitbuddy/users/test/sessions
# → {"id": "<session_id>"}

# 3. WebSocket test
wscat -c "ws://localhost:8080/run_live?app_name=fixitbuddy&user_id=test&session_id=<id>"
# Send binary audio chunks, expect Nova Sonic audio back

# 4. Deploy
cd backend-aws
AWS_ACCOUNT_ID=123456789012 PERPLEXITY_API_KEY=pplx-xxx ./deploy-aws.sh

# 5. Android: Settings → Backend URL → wss://<app-runner-url>
```
