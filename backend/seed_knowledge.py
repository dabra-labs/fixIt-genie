"""Seed Firestore with equipment knowledge base data + Gemini embeddings."""
from __future__ import annotations

import os

from google.cloud import firestore


_EMBED_MODEL = "gemini-embedding-001"
_EMBED_DIMENSIONS = 1536


def _embed_text(text: str, title: str = "") -> list[float]:
    """Generate a Firestore-compatible embedding vector via the Gemini REST API."""
    import requests

    api_key = os.environ["GOOGLE_API_KEY"]
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{_EMBED_MODEL}:embedContent"
    payload = {
        "model": f"models/{_EMBED_MODEL}",
        "content": {"parts": [{"text": text}]},
        "taskType": "RETRIEVAL_DOCUMENT",
        "outputDimensionality": _EMBED_DIMENSIONS,
    }
    if title:
        payload["title"] = title
    resp = requests.post(url, json=payload, params={"key": api_key}, timeout=30)
    resp.raise_for_status()
    return resp.json()["embedding"]["values"]


def seed() -> None:
    """Upload the embedded knowledge base to Firestore with vector embeddings."""
    project = os.environ.get("GOOGLE_CLOUD_PROJECT", "fixitbuddy")
    db = firestore.Client(project=project)

    # Import the embedded knowledge base from tools
    from fixitbuddy.tools import _KNOWLEDGE_BASE

    print(f"Seeding Firestore for project: {project}")
    print("=" * 50)

    # Seed equipment documents with embeddings
    for doc_id, data in _KNOWLEDGE_BASE.items():
        # Build embedding text: combine name, description, error codes, and keywords
        # for best semantic coverage
        embed_parts = [
            data.get("name", ""),
            data.get("description", ""),
            " ".join(data.get("error_codes", [])),
            " ".join(data.get("keywords", [])),
        ]
        embed_text = " ".join(p for p in embed_parts if p)

        print(f"  Embedding {doc_id}...", end=" ", flush=True)
        embedding = _embed_text(embed_text, title=data.get("name", doc_id))

        doc_data = {**data, "embedding": embedding}
        db.collection("equipment").document(doc_id).set(doc_data)
        print(f"✓ (dim={len(embedding)})")

    # Seed category overviews (no embeddings needed — not searched via vector)
    overviews = {
        "automotive": {
            "description": "Common automotive systems and diagnostics",
            "subsystems": ["engine", "electrical", "cooling", "brakes", "exhaust"],
            "common_tools": ["OBD2 scanner", "multimeter", "wrench set", "jack stands"],
        },
        "electrical": {
            "description": "Residential electrical systems",
            "subsystems": ["breaker_panel", "outlets", "switches", "wiring", "gfci"],
            "common_tools": ["voltage tester", "wire strippers", "screwdriver", "multimeter"],
        },
        "appliance": {
            "description": "Household appliances",
            "subsystems": ["washing_machine", "dryer", "dishwasher", "refrigerator"],
            "common_tools": ["multimeter", "screwdriver set", "level", "pliers"],
        },
    }

    for doc_id, data in overviews.items():
        db.collection("equipment_overview").document(doc_id).set(data)
        print(f"  ✓ equipment_overview/{doc_id}")

    print("=" * 50)
    print(
        f"Done! Seeded {len(_KNOWLEDGE_BASE)} equipment docs with embeddings"
        f" + {len(overviews)} overviews."
    )
    print()
    print("Next step: deploy the Firestore vector index before querying:")
    print("  firebase deploy --only firestore:indexes")


if __name__ == "__main__":
    seed()
