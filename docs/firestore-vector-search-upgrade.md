# Knowledge Base Upgrade: Firestore Vector Search

> Status: Planned (post-hackathon or pre-submission if time allows)
> Goal: Replace hardcoded dict in tools.py with semantic vector search using Firestore + Vertex AI Embeddings

---

## Why

Current implementation (`tools.py`) uses a hardcoded Python dict with keyword matching. This is:
- Not semantically aware (misses synonyms, related terms)
- Not scalable (adding knowledge = editing code)
- Weak for judging: "Is there evidence of grounding?" — a vector KB answers this convincingly

Firestore Vector Search + Vertex AI Embeddings gives:
- Semantic similarity search ("car won't start" matches "engine cranking issues")
- Fully Google-native stack (bonus points)
- Decoupled knowledge from code (add docs without redeploying)

---

## Architecture

```
Query (user's spoken problem)
    ↓
Vertex AI text-embedding-004
    ↓ embedding vector
Firestore find_nearest()
    ↓ top-k matching docs
ADK tool returns context to agent
    ↓
Gemini grounds response in retrieved docs
```

---

## Files to Change

### 1. `backend/seed_knowledge.py`
Add embedding generation before Firestore write:

```python
from google.cloud import aiplatform
from vertexai.language_models import TextEmbeddingModel

def embed_text(text: str) -> list[float]:
    model = TextEmbeddingModel.from_pretrained("text-embedding-004")
    embeddings = model.get_embeddings([text])
    return embeddings[0].values

# In seed loop — add embedding field:
doc_data = {
    "content": content,
    "category": category,
    "embedding": embed_text(content),  # ADD THIS
}
db.collection("equipment").document(doc_id).set(doc_data)
```

### 2. `backend/fixitbuddy/tools.py` — `lookup_equipment_knowledge()`
Replace keyword dict lookup with vector search:

```python
from google.cloud import firestore
from vertexai.language_models import TextEmbeddingModel

def lookup_equipment_knowledge(query: str, category: str = None) -> str:
    try:
        db = firestore.Client()
        model = TextEmbeddingModel.from_pretrained("text-embedding-004")

        # Embed the query
        query_embedding = model.get_embeddings([query])[0].values

        # Vector search in Firestore
        collection = db.collection("equipment")
        results = collection.find_nearest(
            vector_field="embedding",
            query_vector=query_embedding,
            distance_measure=firestore.DistanceMeasure.COSINE,
            limit=3,
        ).get()

        if not results:
            return _fallback_knowledge_lookup(query, category)

        docs = [doc.to_dict().get("content", "") for doc in results]
        return "\n\n".join(docs)

    except Exception:
        # Graceful fallback to embedded KB
        return _fallback_knowledge_lookup(query, category)


def _fallback_knowledge_lookup(query: str, category: str = None) -> str:
    # Keep existing hardcoded dict as fallback
    ...
```

### 3. `backend/requirements.txt`
Add:
```
vertexai>=1.0.0
```

---

## Firestore Index Required

Firestore vector search requires a vector index. Add to `firestore.indexes.json`:

```json
{
  "indexes": [],
  "fieldOverrides": [
    {
      "collectionGroup": "equipment",
      "fieldPath": "embedding",
      "indexes": [
        {
          "order": "ASCENDING",
          "queryScope": "COLLECTION"
        }
      ],
      "vectorConfig": {
        "dimension": 768,
        "flat": {}
      }
    }
  ]
}
```

Deploy index:
```bash
firebase deploy --only firestore:indexes
```

---

## GCP Requirements

- Vertex AI API must be enabled (deploy.sh already does this)
- `text-embedding-004` model: 768-dimension embeddings
- Firestore vector index: deploy once before seeding

---

## Estimated Effort

| Task | Time |
|------|------|
| Update seed_knowledge.py | 30 min |
| Update tools.py lookup | 45 min |
| Deploy Firestore index | 15 min |
| Re-seed knowledge base | 10 min |
| Test + verify | 30 min |
| **Total** | **~2 hours** |

---

## Notes

- Keep hardcoded dict as fallback — tool must never fail silently
- text-embedding-004 is 768-dim; update vectorConfig dimension accordingly
- Cost: ~$0.00002 per query (negligible)
- No Android app changes needed
- No Cloud Run redeployment needed (only backend Python files change)
