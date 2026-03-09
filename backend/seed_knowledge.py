"""Seed Firestore with equipment knowledge base data."""
from __future__ import annotations

import os

from google.cloud import firestore


def seed() -> None:
    """Upload the embedded knowledge base to Firestore for production use."""
    project = os.environ.get("GOOGLE_CLOUD_PROJECT", "fixitbuddy")
    db = firestore.Client(project=project)

    # Import the embedded knowledge base from tools
    from fixitbuddy.tools import _KNOWLEDGE_BASE

    print(f"Seeding Firestore for project: {project}")
    print("=" * 50)

    # Seed equipment documents
    for doc_id, data in _KNOWLEDGE_BASE.items():
        db.collection("equipment").document(doc_id).set(data)
        print(f"  ✓ equipment/{doc_id}")

    # Seed category overviews
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
    print(f"Done! Seeded {len(_KNOWLEDGE_BASE)} equipment docs + {len(overviews)} overviews.")


if __name__ == "__main__":
    seed()
