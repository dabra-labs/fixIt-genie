"""FixIt Buddy — Agent Tools (Function Calling)."""

from __future__ import annotations

import logging
import os
from typing import Any

from google.cloud import firestore

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Firestore client (lazy singleton)
# ---------------------------------------------------------------------------

_db: firestore.Client | None = None


def _get_db() -> firestore.Client | None:
    global _db
    if _db is None:
        try:
            _db = firestore.Client(
                project=os.environ.get("GOOGLE_CLOUD_PROJECT", "fixitbuddy")
            )
        except Exception:
            logger.warning("Firestore unavailable — using embedded knowledge base", exc_info=True)
            _db = None
    return _db


# ===== Embedded Knowledge Base (fallback when Firestore is unavailable) =====
_KNOWLEDGE_BASE: dict[str, dict[str, Any]] = {
    "automotive_oil_system": {
        "category": "automotive",
        "name": "Engine Oil System",
        "description": "Engine lubrication system including oil level, oil pressure, and related components",
        "error_codes": ["P0520", "P0521", "P0522", "P0523", "P0524"],
        "keywords": ["oil", "dipstick", "engine", "lubrication", "ticking", "low oil", "oil light", "oil pressure"],
        "diagnostic_steps": [
            {"step": 1, "instruction": "Locate the oil dipstick — usually has a yellow or orange handle", "visual_cue": "Look for a looped handle near the engine block"},
            {"step": 2, "instruction": "Pull the dipstick out and wipe it clean with a rag", "visual_cue": "The dipstick has two marks — MIN and MAX"},
            {"step": 3, "instruction": "Reinsert fully, then pull out again to read the level", "visual_cue": "Oil should be between MIN and MAX marks"},
            {"step": 4, "instruction": "Check oil color — should be amber/brown, not black or milky", "visual_cue": "Milky oil suggests coolant leak (head gasket issue)"},
            {"step": 5, "instruction": "If low, add oil through the oil filler cap (usually on top of engine)", "visual_cue": "Cap often has an oil can icon"}
        ],
        "common_issues": [
            {"issue": "Ticking/tapping noise from engine", "cause": "Low oil level — insufficient lubrication", "fix": "Top up oil to MAX mark. If noise persists, check for oil leak underneath"},
            {"issue": "Oil light on dashboard", "cause": "Low oil pressure — could be low level, worn pump, or sensor issue", "fix": "Check level first. If level OK, do not drive — have it towed to a mechanic"},
            {"issue": "Oil looks milky/frothy", "cause": "Coolant mixing with oil — possible head gasket failure", "fix": "Do not drive. This needs professional diagnosis immediately"}
        ],
        "safety_notes": ["Engine may be hot — let it cool for 10+ minutes", "Keep hands away from belts and fans", "Oil is slippery — clean any spills immediately"]
    },
    "automotive_battery": {
        "category": "automotive",
        "name": "Car Battery and Electrical",
        "description": "Battery, alternator, and starting system diagnostics",
        "error_codes": ["P0562", "P0563", "P0620", "P0621"],
        "keywords": ["battery", "won't start", "dead battery", "jump start", "alternator", "clicking", "electrical"],
        "diagnostic_steps": [
            {"step": 1, "instruction": "Locate the battery — usually in the engine bay, sometimes in the trunk", "visual_cue": "Rectangular box with two terminals (+ red, - black)"},
            {"step": 2, "instruction": "Check for corrosion on terminals", "visual_cue": "White/blue-green crusty buildup on terminals"},
            {"step": 3, "instruction": "Check if battery terminals are tight — they shouldn't wiggle", "visual_cue": "Loose connection can cause intermittent starting issues"},
            {"step": 4, "instruction": "If corroded, clean with baking soda + water and a wire brush", "visual_cue": "Terminals should be shiny metal after cleaning"},
            {"step": 5, "instruction": "If battery is more than 3-4 years old, it may need replacement", "visual_cue": "Check date sticker on battery top"}
        ],
        "common_issues": [
            {"issue": "Car won't start — clicking sound", "cause": "Weak battery or poor connection", "fix": "Try cleaning terminals. If still clicking, battery likely needs jump or replacement"},
            {"issue": "Car won't start — no sound at all", "cause": "Completely dead battery or blown fuse", "fix": "Check battery voltage if possible. Jump start or replace battery"},
            {"issue": "Battery keeps dying", "cause": "Alternator not charging, parasitic drain, or old battery", "fix": "Check alternator belt, then have alternator tested at auto parts store (usually free)"}
        ],
        "safety_notes": ["Battery acid is corrosive — wear gloves and eye protection", "Never short the terminals together — risk of explosion", "Remove negative terminal first when disconnecting, connect last when reconnecting"]
    },
    "automotive_coolant": {
        "category": "automotive",
        "name": "Cooling System",
        "description": "Coolant level, overheating, radiator, and thermostat diagnostics",
        "error_codes": ["P0115", "P0116", "P0117", "P0125", "P0128"],
        "keywords": ["coolant", "overheating", "temperature", "radiator", "thermostat", "steam", "hot", "antifreeze"],
        "diagnostic_steps": [
            {"step": 1, "instruction": "Locate the coolant reservoir — translucent plastic container with MIN/MAX marks", "visual_cue": "Usually labeled 'COOLANT' or has a temperature warning symbol"},
            {"step": 2, "instruction": "Check coolant level against MIN/MAX marks (engine COLD only)", "visual_cue": "Coolant is typically green, orange, or pink"},
            {"step": 3, "instruction": "Look under the car for puddles", "visual_cue": "Green/orange liquid = coolant leak"},
            {"step": 4, "instruction": "Check radiator cap condition (engine COLD only)", "visual_cue": "Cracked or worn rubber seal means it can't hold pressure"}
        ],
        "common_issues": [
            {"issue": "Temperature gauge in the red", "cause": "Low coolant, stuck thermostat, or failed water pump", "fix": "STOP driving immediately. Let engine cool 30+ min. Check coolant level when cold"},
            {"issue": "Steam from under hood", "cause": "Coolant leak hitting hot engine", "fix": "STOP immediately. Do not open hood until steam stops. Then check coolant level when cold"},
            {"issue": "Coolant level keeps dropping", "cause": "Leak in system — hoses, radiator, water pump, or head gasket", "fix": "Check hoses for cracks/wet spots. If not visible, needs pressure test at shop"}
        ],
        "safety_notes": ["NEVER open radiator cap when engine is hot — pressurized steam will cause severe burns", "Let engine cool at least 30 minutes before checking coolant", "Coolant is toxic to pets — clean up any spills"]
    },
    "electrical_breaker_panel": {
        "category": "electrical",
        "name": "Residential Breaker Panel",
        "description": "Home electrical breaker panel — circuit breakers, tripped breakers, GFCI issues",
        "error_codes": [],
        "keywords": ["breaker", "circuit", "panel", "tripped", "power out", "electricity", "fuse", "outlet"],
        "diagnostic_steps": [
            {"step": 1, "instruction": "Open the breaker panel door", "visual_cue": "Metal door, usually in garage, basement, or utility closet"},
            {"step": 2, "instruction": "Look for any breaker that's in the middle position (not fully ON or OFF)", "visual_cue": "A tripped breaker sits between ON and OFF — it won't be aligned with the others"},
            {"step": 3, "instruction": "To reset: push the tripped breaker fully to OFF first, then flip to ON", "visual_cue": "You should hear/feel a click when it engages in ON position"},
            {"step": 4, "instruction": "If it trips again immediately, there's a fault on that circuit — do NOT keep resetting", "visual_cue": "Repeated tripping = short circuit or overloaded circuit"},
            {"step": 5, "instruction": "Check what's plugged in on that circuit — unplug everything, then reset", "visual_cue": "If it stays on with nothing plugged in, one of your devices has a short"}
        ],
        "common_issues": [
            {"issue": "One room lost power", "cause": "Tripped breaker — often from overloaded circuit", "fix": "Find and reset the tripped breaker. If it trips again, reduce load on that circuit"},
            {"issue": "Breaker keeps tripping", "cause": "Overloaded circuit, short circuit, or ground fault", "fix": "Unplug everything on circuit, reset. Add devices back one at a time to find the culprit"},
            {"issue": "GFCI outlet won't reset", "cause": "Ground fault on circuit, or failed GFCI outlet", "fix": "Unplug everything from GFCI outlets on that circuit. Press RESET. If still won't reset, the GFCI may need replacement"},
            {"issue": "Burning smell from panel", "cause": "Loose connection, overheated wire, or failing breaker", "fix": "DO NOT TOUCH. Turn off main breaker if safe to do so. Call an electrician immediately"}
        ],
        "safety_notes": ["Never touch anything inside the panel with wet hands", "Do not remove the panel cover — exposed bus bars carry lethal voltage", "If you see scorch marks, melted plastic, or smell burning — call an electrician immediately", "Keep the area around the panel clear — 3 feet clearance required by code"]
    },
    "electrical_gfci": {
        "category": "electrical",
        "name": "GFCI Outlets",
        "description": "Ground Fault Circuit Interrupter outlets — bathroom, kitchen, outdoor outlets",
        "error_codes": [],
        "keywords": ["gfci", "outlet", "reset", "test", "bathroom", "kitchen", "outdoor", "no power"],
        "diagnostic_steps": [
            {"step": 1, "instruction": "Find the GFCI outlet — it has TEST and RESET buttons in the center", "visual_cue": "Usually in bathrooms, kitchens, garages, and outdoor locations"},
            {"step": 2, "instruction": "Press the RESET button firmly", "visual_cue": "You should hear a click and power should restore"},
            {"step": 3, "instruction": "If RESET won't click in, the GFCI detected a fault — unplug everything first", "visual_cue": "Try pressing TEST first, then RESET"},
            {"step": 4, "instruction": "A single GFCI can protect multiple outlets downstream", "visual_cue": "Check other outlets in bathroom/kitchen — they may be on the same GFCI"}
        ],
        "common_issues": [
            {"issue": "Bathroom outlets all dead", "cause": "GFCI tripped — could be in that bathroom or another bathroom", "fix": "Find and reset the GFCI outlet. Check ALL bathrooms — one GFCI often protects multiple rooms"},
            {"issue": "GFCI trips when using hair dryer", "cause": "Hair dryer pulling too much current or has a ground fault", "fix": "Try a different hair dryer. If same issue, the circuit may be overloaded or the GFCI is sensitive"}
        ],
        "safety_notes": ["GFCI protects you from electrocution — never bypass it", "Test GFCI outlets monthly by pressing TEST, then RESET"]
    },
    "appliance_washing_machine": {
        "category": "appliance",
        "name": "Washing Machine",
        "description": "Common washing machine error codes and troubleshooting across major brands",
        "error_codes": ["E1", "E2", "E3", "E4", "F1", "F2", "F21", "UE", "OE", "LE", "dE", "IE"],
        "keywords": ["washing machine", "washer", "laundry", "won't drain", "won't spin", "error code", "leak", "vibration"],
        "diagnostic_steps": [
            {"step": 1, "instruction": "Note the error code displayed", "visual_cue": "Error code appears on the display panel — may be letters + numbers"},
            {"step": 2, "instruction": "Try power cycling: unplug for 60 seconds, then plug back in", "visual_cue": "This resets the control board and clears many temporary errors"},
            {"step": 3, "instruction": "Check the door/lid — make sure it's fully closed and latched", "visual_cue": "Many errors are caused by the door not being detected as closed"},
            {"step": 4, "instruction": "Check water supply valves behind the machine — both should be fully open", "visual_cue": "Hot and cold valves, handles should be parallel to the hose (open)"},
            {"step": 5, "instruction": "Check the drain hose — shouldn't be kinked or inserted too far into the drain pipe", "visual_cue": "Drain hose should only go 6-8 inches into the standpipe"}
        ],
        "common_issues": [
            {"issue": "Error E4 or IE (Water supply issue)", "cause": "Water not reaching the machine", "fix": "Check that supply valves are fully open. Check inlet hose for kinks. Clean inlet filter screens"},
            {"issue": "Error UE (Unbalanced load)", "cause": "Clothes bunched to one side during spin", "fix": "Redistribute clothes evenly in the drum. Don't overload. Check that machine is level"},
            {"issue": "Error OE or F21 (Drain issue)", "cause": "Water not draining properly", "fix": "Check drain hose for kinks. Clean the drain pump filter (small door at bottom front). Remove any debris"},
            {"issue": "Error dE (Door issue)", "cause": "Door/lid not properly closed or latch malfunction", "fix": "Clean the door seal/gasket. Check for obstructions preventing door from closing"},
            {"issue": "Error LE (Motor issue)", "cause": "Motor overloaded or rotor position sensor issue", "fix": "Reduce load size. Unplug for 30 min and retry. If persistent, motor may need professional service"}
        ],
        "safety_notes": ["Always unplug before inspecting internal components", "Water + electricity = danger — mop up any water before working near outlets", "The drain pump filter may release water when opened — have towels ready"]
    },
    "appliance_refrigerator_lg": {
        "category": "appliance",
        "name": "LG Refrigerator",
        "description": "LG refrigerator troubleshooting — cooling issues, error codes, ice maker, water dispenser",
        "error_codes": ["Er IF", "Er FF", "Er CF", "Er dF", "Er rF", "Er CO", "Er FS", "Er IS", "Er SS", "Er 1F", "Er FF", "CL", "dH"],
        "keywords": ["refrigerator", "fridge", "not cooling", "not cold", "lg", "freezer", "ice maker", "ice", "water dispenser", "compressor", "temperature", "warm", "er if", "er ff", "er cf"],
        "diagnostic_steps": [
            {"step": 1, "instruction": "Check the temperature display — fridge should be 37°F (3°C), freezer 0°F (-18°C)", "visual_cue": "Display panel is usually on the front of the door or inside at the top"},
            {"step": 2, "instruction": "Check if the condenser coils on the back/bottom are dusty", "visual_cue": "Dusty coils look grey/brown and restrict airflow — clean with a vacuum brush attachment"},
            {"step": 3, "instruction": "Check the door seals all around — they should be airtight", "visual_cue": "Close a piece of paper in the door — if it slides out easily, the seal is weak"},
            {"step": 4, "instruction": "Check that the door vents inside aren't blocked by food containers", "visual_cue": "Cold air vents are usually on the back wall inside — leave a few inches of clearance"},
            {"step": 5, "instruction": "Listen for the compressor — it should cycle on and off", "visual_cue": "Compressor is at the back bottom — a humming sound means it's running"}
        ],
        "common_issues": [
            {"issue": "Fridge not cooling but freezer works", "cause": "Evaporator fan blocked or failed, or damper stuck closed", "fix": "Check if fan runs when door is open (hold door switch). Ice buildup on back wall means defrost issue — run manual defrost"},
            {"issue": "Neither fridge nor freezer cooling", "cause": "Compressor not running, refrigerant leak, or start relay failure", "fix": "Listen for compressor — if silent, check start relay (small box on compressor side). Shake it — a rattle means it needs replacement (~$10 part)"},
            {"issue": "Error Er IF (Ice maker fan)", "cause": "Ice maker fan is blocked or frozen", "fix": "Remove all ice from ice maker. Power cycle the fridge. If error persists, ice maker fan motor may need replacement"},
            {"issue": "Error Er FF (Freezer fan)", "cause": "Freezer evaporator fan failure", "fix": "Power cycle the fridge. If error persists, evaporator fan needs replacement. Defrost first to check if ice is blocking it"},
            {"issue": "Error Er CF (Condenser fan)", "cause": "Condenser fan at back/bottom is blocked or failed", "fix": "Clean debris from condenser area at back bottom. Check fan blade isn't blocked. Power cycle"},
            {"issue": "Error Er dF (Defrost)", "cause": "Defrost heater or defrost sensor failure", "fix": "Run a manual defrost cycle: press and hold both temperature buttons for 3-5 seconds. If error returns, defrost heater needs replacement"},
            {"issue": "Ice maker not making ice", "cause": "Freezer too warm, water supply issue, or ice maker turned off", "fix": "Check freezer is at 0°F. Check water line isn't kinked. Check ice maker arm is in DOWN position. Check water filter age"},
            {"issue": "Fridge making loud noise", "cause": "Evaporator fan hitting ice buildup, or condenser fan debris", "fix": "Ice hitting fan = defrost needed. Check back of freezer for ice buildup. Clean condenser area at back bottom"},
            {"issue": "Water dispenser not working", "cause": "Water filter clogged, frozen water line, or door switch", "fix": "Replace water filter if older than 6 months. Check filter is fully seated. Inspect dispenser actuator (small tab in door frame)"}
        ],
        "safety_notes": ["Unplug before cleaning condenser coils", "Do not use sharp objects to chip ice — can puncture refrigerant lines", "If you smell a chemical odor, refrigerant may be leaking — ventilate and call a technician"],
        "model_specific": {
            "lg_linear_compressor": "LG Linear Compressor fridges are very quiet — if you hear loud knocking, the compressor may be failing",
            "smart_diagnosis": "LG Smart Diagnosis: place phone against the speaker on fridge and call LG support — the fridge transmits diagnostic tones",
            "filter_reset": "After replacing water filter, press and hold FILTER button for 3 seconds to reset the filter indicator"
        }
    },
    "appliance_dishwasher": {
        "category": "appliance",
        "name": "Dishwasher",
        "description": "Common dishwasher error codes and troubleshooting",
        "error_codes": ["E1", "E2", "E3", "E4", "E15", "E22", "E24", "E25"],
        "keywords": ["dishwasher", "dishes", "won't drain", "won't start", "error code", "leak", "not cleaning"],
        "diagnostic_steps": [
            {"step": 1, "instruction": "Note any error code or flashing lights on the display", "visual_cue": "Some models flash LED lights instead of showing alphanumeric codes"},
            {"step": 2, "instruction": "Check if the door is fully latched — the dishwasher won't start unless it detects the door is closed", "visual_cue": "Push firmly until you hear the latch click"},
            {"step": 3, "instruction": "Check for standing water in the bottom of the tub", "visual_cue": "A small amount is normal; several inches is a drain problem"},
            {"step": 4, "instruction": "Check the drain filter at the bottom — remove and clean any debris", "visual_cue": "Usually a circular filter that twists out, located at the bottom center"}
        ],
        "common_issues": [
            {"issue": "Error E15 (Bosch) — Water in base", "cause": "Leak detected in the base pan", "fix": "Tilt the dishwasher forward slightly to drain the base pan. Check for hose leaks. Reset by unplugging for 1 minute"},
            {"issue": "Error E24/E25 — Drain issue", "cause": "Drain hose kinked or clogged", "fix": "Check drain hose under sink for kinks. Clean the drain filter. Run garbage disposal to clear shared drain line"},
            {"issue": "Dishwasher won't start", "cause": "Door latch, child lock, or delayed start", "fix": "Ensure door is latched. Check if child lock is activated (hold button 3 sec). Check if delay start is set"}
        ],
        "safety_notes": ["Always turn off the dishwasher and disconnect power before accessing internal components", "Be careful of sharp items when cleaning the filter"]
    }
}


def lookup_equipment_knowledge(
    query: str, category: str = "", error_code: str = ""
) -> dict[str, Any]:
    """Look up equipment knowledge using semantic vector search.

    Searches the knowledge base by semantic similarity — "engine oil pressure
    alarm" will match the oil system document even without exact keyword overlap.

    Args:
        query: Natural language description of what to look up
        category: Equipment category - one of: automotive, electrical, appliance
        error_code: Specific error code to look up (e.g., "E4", "P0301", "F21")

    Returns:
        Relevant equipment knowledge including diagnostic steps and solutions
    """
    # Build the search text: combine query, error code, and category for best embedding
    search_text = query
    if error_code:
        search_text = f"{error_code} {query}"
    if category:
        search_text = f"{category} {search_text}"

    results: list[dict[str, Any]] = []

    # Primary: Firestore vector search (semantic similarity via text-embedding-004)
    db = _get_db()
    if db:
        try:
            import requests as _requests

            _api_key = os.environ.get("GOOGLE_API_KEY", "")
            _embed_model = "gemini-embedding-001"
            _embed_url = f"https://generativelanguage.googleapis.com/v1beta/models/{_embed_model}:embedContent"
            _embed_resp = _requests.post(
                _embed_url,
                json={"model": f"models/{_embed_model}", "content": {"parts": [{"text": search_text}]}},
                params={"key": _api_key},
                timeout=15,
            )
            _embed_resp.raise_for_status()
            query_embedding = _embed_resp.json()["embedding"]["values"]

            collection = db.collection("equipment")
            vector_results = collection.find_nearest(
                vector_field="embedding",
                query_vector=query_embedding,
                distance_measure=firestore.DistanceMeasure.COSINE,
                limit=3,
            ).get()

            for doc in vector_results:
                data = doc.to_dict()
                # Strip the embedding field — it's large and not useful to the agent
                data.pop("embedding", None)
                results.append(data)

        except Exception:
            logger.warning("Vector search failed — falling back to embedded KB", exc_info=True)

    # Fallback: keyword matching against embedded knowledge base
    if not results:
        results = _keyword_lookup(query, category, error_code)

    if results:
        return {"found": True, "results": results[:3]}
    return {
        "found": False,
        "message": "No specific knowledge found. Use general expertise and google_search for specific model information.",
    }


def _keyword_lookup(
    query: str, category: str = "", error_code: str = ""
) -> list[dict[str, Any]]:
    """Keyword-based fallback lookup against the embedded knowledge base."""
    results: list[dict[str, Any]] = []
    query_lower = query.lower()
    for _doc_id, data in _KNOWLEDGE_BASE.items():
        if error_code and error_code.upper() in data.get("error_codes", []):
            results.append(data)
            continue
        if category and data.get("category") == category.lower():
            if any(kw in query_lower for kw in data.get("keywords", [])):
                results.append(data)
        elif any(kw in query_lower for kw in data.get("keywords", [])):
            results.append(data)
    return results


def get_safety_warnings(
    action_type: str, equipment_category: str = ""
) -> dict[str, Any]:
    """Get safety warnings before guiding a physical action. MUST be called before any hands-on instruction.

    Args:
        action_type: Type of action - one of: electrical, mechanical, fluid, pressure, heat, chemical
        equipment_category: Equipment category for context-specific warnings

    Returns:
        Safety warnings and precautions that MUST be communicated to the user
    """
    warnings: dict[str, list[str]] = {
        "electrical": [
            "Ensure power is completely disconnected before touching any wiring",
            "Use a non-contact voltage tester to verify circuits are dead",
            "Never work on live electrical panels — risk of electrocution",
            "Keep one hand in your pocket when working near electrical panels",
            "If you see scorch marks, melted plastic, or smell burning — STOP and call an electrician"
        ],
        "mechanical": [
            "Ensure equipment is powered off and cannot start unexpectedly",
            "Watch for pinch points — fingers can be caught in moving parts",
            "Wear appropriate PPE: safety glasses, gloves",
            "If something is stuck, don't force it — forcing can cause sudden release and injury"
        ],
        "fluid": [
            "Beware of hot fluids — coolant, oil, and transmission fluid can cause burns",
            "Have rags or absorbent material ready for spills",
            "Some fluids are toxic — avoid skin contact and don't ingest",
            "Properly dispose of used fluids — don't pour down drains"
        ],
        "pressure": [
            "NEVER open pressurized systems without depressurizing first",
            "Stand to the side when loosening fittings — fluid may spray",
            "Check pressure gauges before starting any work",
            "Compressed air can cause serious injury — never point at yourself or others"
        ],
        "heat": [
            "Allow hot equipment to cool before touching",
            "Use thermal gloves when handling hot components",
            "Hot surfaces may not look hot — always test carefully",
            "Keep flammable materials away from hot components"
        ],
        "chemical": [
            "Work in a ventilated area",
            "Wear chemical-resistant gloves and eye protection",
            "Know the location of the nearest water source for rinsing",
            "Read product labels before using any chemicals"
        ]
    }

    result = warnings.get(action_type, [
        "Proceed with caution",
        "Wear appropriate personal protective equipment",
        "If unsure, consult a professional"
    ])

    response: dict[str, Any] = {
        "warnings": result,
        "general": "Always prioritize safety. If at any point you feel unsafe, stop and call a professional.",
        "action_type": action_type,
    }
    if equipment_category:
        response["equipment_category"] = equipment_category
    return response


def analyze_youtube_repair_video(
    youtube_url: str,
    question: str = "Extract step-by-step repair instructions from this video transcript. Be concise and numbered.",
) -> dict[str, Any]:
    """Extract repair steps from a YouTube video.

    Primary: fetches the video transcript via youtube-transcript-api (works for
    any English-captioned video, fast, no Gemini token cost for the video).
    Fallback: passes the URL to Gemini REST API (only works for videos Google
    has indexed at scale — mostly viral/very popular content).

    Args:
        youtube_url: Full YouTube watch URL (https://www.youtube.com/watch?v=...)
        question: What to extract from the video

    Returns:
        Dict with 'found' (bool), 'steps' (str), 'source' (url)
    """
    # Extract video ID from URL
    import re
    match = re.search(r"[?&]v=([A-Za-z0-9_-]{11})", youtube_url)
    if not match:
        return {"found": False, "error": "Could not parse video ID from URL", "source": youtube_url}
    video_id = match.group(1)

    transcript_text: str | None = None

    # Strategy 1: transcript API (reliable for any English-captioned video)
    try:
        from youtube_transcript_api import YouTubeTranscriptApi
        api = YouTubeTranscriptApi()
        transcript = api.fetch(video_id)
        transcript_text = " ".join(s.text for s in transcript)
    except Exception as e:
        logger.info("Transcript API failed for %s: %s — trying Gemini URL fallback", video_id, e)

    if transcript_text:
        try:
            from google import genai
            client = genai.Client(api_key=os.environ.get("GOOGLE_API_KEY"))
            response = client.models.generate_content(
                model=os.environ.get("AGENT_MODEL_TEXT", "gemini-2.5-flash"),
                contents=f"{question}\n\nTranscript:\n{transcript_text[:12000]}",
            )
            return {"found": True, "steps": response.text, "source": youtube_url}
        except Exception as e:
            # Propagate rate limit errors — don't silently fall through
            if "429" in str(e) or "RESOURCE_EXHAUSTED" in str(e):
                raise
            logger.warning("Gemini summarization of transcript failed: %s", e)

    # Strategy 2: Gemini native YouTube URL (works for Google-indexed popular videos)
    try:
        from google import genai
        from google.genai import types as gtypes
        client = genai.Client(api_key=os.environ.get("GOOGLE_API_KEY"))
        response = client.models.generate_content(
            model=os.environ.get("AGENT_MODEL_TEXT", "gemini-2.5-flash"),
            contents=[
                gtypes.Part.from_uri(file_uri=youtube_url, mime_type="video/mp4"),
                gtypes.Part.from_text(text=question),
            ],
        )
        # Detect if model actually processed the video vs returned a refusal
        if "cannot" not in response.text.lower()[:60] and "don't have" not in response.text.lower()[:60]:
            return {"found": True, "steps": response.text, "source": youtube_url}
    except Exception as e:
        logger.warning("Gemini URL fallback failed: %s", e)

    return {
        "found": False,
        "error": "Could not extract content from this video (no captions available and video not indexed by Gemini).",
        "source": youtube_url,
        "suggestion": "Share the video link with the user so they can watch it directly.",
    }


def lookup_user_manual(brand: str, model_number: str) -> dict[str, Any]:
    """Find and extract content from a product user manual.

    Uses Gemini grounded search to locate the official PDF, then fetches and
    extracts troubleshooting steps, error codes, and key specs from it.

    Args:
        brand: Appliance brand (e.g., "LG", "Samsung", "Bosch")
        model_number: Model number (e.g., "WM3900HWA", "WF45R6100AW")

    Returns:
        Dict with 'found' (bool), 'content' (extracted text), 'url' (source)
    """
    import io
    import requests

    try:
        from google import genai
        from google.genai import types as gtypes

        client = genai.Client(api_key=os.environ.get("GOOGLE_API_KEY"))

        # Step 1: Use grounded search to find the official manual PDF URL
        search_result = client.models.generate_content(
            model=os.environ.get("AGENT_MODEL_TEXT", "gemini-2.5-flash"),
            contents=(
                f"Find the official PDF user manual download URL for {brand} model {model_number}. "
                "Return only the direct URL to the PDF file, nothing else."
            ),
            config=gtypes.GenerateContentConfig(
                tools=[gtypes.Tool(google_search=gtypes.GoogleSearch())]
            ),
        )
        manual_url = search_result.text.strip()

        # Step 2: Fetch and parse the PDF
        if manual_url.startswith("http") and ".pdf" in manual_url.lower():
            pdf_resp = requests.get(
                manual_url, timeout=15, headers={"User-Agent": "Mozilla/5.0"}
            )
            if pdf_resp.status_code == 200:
                from pypdf import PdfReader

                reader = PdfReader(io.BytesIO(pdf_resp.content))
                # First 20 pages covers intro, error codes, and troubleshooting
                text = "\n".join(
                    page.extract_text() or "" for page in reader.pages[:20]
                )
                summary = client.models.generate_content(
                    model=os.environ.get("AGENT_MODEL_TEXT", "gemini-2.5-flash"),
                    contents=(
                        f"From this {brand} {model_number} user manual, extract: "
                        "(1) all error codes and their meanings, "
                        "(2) troubleshooting steps, "
                        "(3) key specifications.\n\n" + text[:12000]
                    ),
                )
                return {
                    "found": True,
                    "brand": brand,
                    "model": model_number,
                    "content": summary.text,
                    "url": manual_url,
                }
    except Exception as e:
        logger.warning("Manual lookup failed: %s", e)

    return {
        "found": False,
        "message": f"Could not fetch manual for {brand} {model_number}.",
        "search_hint": f"{brand} {model_number} user manual PDF",
    }


def log_diagnostic_step(
    step_number: int, description: str, observation: str = "", result: str = ""
) -> dict[str, Any]:
    """Log a diagnostic step for the session transcript.

    Args:
        step_number: Sequential step number
        description: What action was taken or recommended
        observation: What was observed (visual or audio)
        result: Outcome of the step

    Returns:
        Confirmation that the step was logged
    """
    step = {
        "step": step_number,
        "description": description,
        "observation": observation,
        "result": result,
    }
    return {"logged": True, "step": step}
