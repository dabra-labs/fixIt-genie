# KB Integration via MCP: Confluence, Notion, Google Drive, etc.

> Status: Planned (future roadmap / enterprise pitch)
> Goal: Feed FixIt Buddy's knowledge base from existing org documentation via MCP connectors

---

## The Story

Instead of manually curating a knowledge base, FixIt Buddy connects to wherever
your team already stores equipment knowledge — Confluence wikis, Notion pages,
PDF manuals in Google Drive — and automatically ingests, embeds, and makes
that knowledge searchable via the repair agent.

**"Point it at your docs. It learns your equipment."**

---

## Architecture

```
Knowledge Sources (via MCP)
├── Confluence → equipment SOPs, maintenance runbooks
├── Notion     → team wikis, troubleshooting guides
├── Google Drive → PDF manuals, spec sheets, warranties
├── SharePoint → enterprise asset documentation
└── GitHub     → technical docs, issue history

        ↓ MCP tools (fetch + chunk)

ADK Agent (ingestion pipeline)
    - fetch_confluence_page(space, title)
    - fetch_notion_database(db_id)
    - fetch_gdrive_document(file_id)

        ↓

Vertex AI text-embedding-004
    (chunk → embed)

        ↓

Firestore Vector Search
    (store with source metadata)

        ↓

Repair Agent grounds answers in org's actual docs
```

---

## MCP Servers to Use

| Source | MCP Server | Key tools |
|---|---|---|
| Confluence | `@anthropic/mcp-server-confluence` | `get_page`, `search_content` |
| Notion | `@anthropic/mcp-server-notion` | `query_database`, `get_page` |
| Google Drive | `@anthropic/mcp-server-gdrive` | `search_files`, `get_file_content` |
| GitHub | `@anthropic/mcp-server-github` | `search_code`, `get_file_contents` |

---

## Implementation Plan

### Phase 1: MCP ingestion tool in ADK agent

Add a new ADK tool: `ingest_knowledge_from_source(source_type, source_id)`

```python
# tools.py — new tool
async def ingest_knowledge_from_source(
    source_type: str,  # "confluence", "notion", "gdrive"
    source_id: str,    # page ID, database ID, file ID
    category: str,     # equipment category tag
) -> str:
    """Fetch a document via MCP, embed it, store in Firestore."""

    # 1. Fetch content via MCP connector
    content = await mcp_fetch(source_type, source_id)

    # 2. Chunk into segments
    chunks = chunk_document(content, max_tokens=512)

    # 3. Embed each chunk
    model = TextEmbeddingModel.from_pretrained("text-embedding-004")

    # 4. Store in Firestore with source metadata
    for i, chunk in enumerate(chunks):
        embedding = model.get_embeddings([chunk])[0].values
        db.collection("equipment").add({
            "content": chunk,
            "category": category,
            "source_type": source_type,
            "source_id": source_id,
            "chunk_index": i,
            "embedding": embedding,
            "ingested_at": firestore.SERVER_TIMESTAMP,
        })

    return f"Ingested {len(chunks)} chunks from {source_type}:{source_id}"
```

### Phase 2: Admin endpoint (optional)

Expose a Cloud Run endpoint for triggering ingestion:

```
POST /ingest
{
  "source_type": "confluence",
  "source_id": "~123456789",
  "category": "hvac"
}
```

### Phase 3: Scheduled sync (optional)

Cloud Scheduler job to re-ingest updated docs periodically.

---

## New Use Case Onboarding Flow

With this in place, adding a new use case (e.g. HVAC) becomes:

```
1. Point to Confluence space / Notion DB / Google Drive folder
2. POST /ingest for each relevant page
3. Done — agent knows your HVAC equipment
```

No code changes. No redeployment. Just data.

---

## Hackathon Angle

Even without implementing this fully, it can be mentioned in the submission as:
- Architecture diagram shows MCP ingestion layer
- "Extensible to any enterprise knowledge source via MCP"
- Positions FixIt Buddy as a platform, not just a demo

---

## Notes

- Each Firestore doc should store `source_type` + `source_id` for traceability
- Deduplication: hash content chunks to avoid re-embedding same content
- Access control: MCP auth tokens stored as Cloud Run env vars / Secret Manager
- PDF manuals: need PDF extraction step before embedding (pypdf or Google Document AI)
