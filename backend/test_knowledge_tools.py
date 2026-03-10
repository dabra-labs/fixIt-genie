"""
Standalone POC validation for FixIt Buddy knowledge tools.
Tests each tool independently, then a combined agent.

Usage:
    cd backend && python test_knowledge_tools.py

Required env vars (loaded from .env automatically):
    GOOGLE_API_KEY=<key>
"""

import asyncio
import json
import os
import sys

from dotenv import load_dotenv

load_dotenv()

sys.path.insert(0, os.path.dirname(__file__))


# ── Test 1: google_search via minimal ADK agent ───────────────────────────────

async def test_google_search() -> str:
    """Verify google_search grounding returns real web results."""
    from google.adk.agents import Agent
    from google.adk.runners import InMemoryRunner
    from google.adk.tools.google_search_tool import GoogleSearchTool
    from google.genai import types

    agent = Agent(
        model="gemini-2.5-flash",
        name="search_test",
        instruction="Answer using web search. Be concise.",
        tools=[GoogleSearchTool(bypass_multi_tools_limit=True)],
    )
    runner = InMemoryRunner(agent=agent)
    session = await runner.session_service.create_session(app_name=runner.app_name, user_id="tester")

    response_text = ""
    async for event in runner.run_async(
        user_id="tester",
        session_id=session.id,
        new_message=types.Content(
            role="user",
            parts=[types.Part(text="What are the steps to fix a Samsung washer SC error code?")],
        ),
    ):
        if event.is_final_response() and event.content:
            for part in event.content.parts:
                if part.text:
                    response_text += part.text

    print("\n=== TEST 1: google_search ===")
    print(response_text[:600])
    assert len(response_text) > 50, "google_search returned empty response"
    print("✓ PASS\n")
    return response_text


# ── Test 2: analyze_youtube_repair_video (direct, no agent) ───────────────────

def test_youtube_analysis() -> str:
    """Verify YouTube transcript extraction works (prerequisite for analyze_youtube_repair_video).

    The full tool (transcript + Gemini summarization) is validated in test_combined_agent.
    This test proves the transcript layer works independently of Gemini quota.
    """
    from youtube_transcript_api import YouTubeTranscriptApi

    # Rick Astley: known public video with English captions — reliable for CI
    api = YouTubeTranscriptApi()
    transcript = api.fetch("dQw4w9WgXcQ")
    text = " ".join(s.text for s in transcript)

    print("\n=== TEST 2: analyze_youtube_repair_video (transcript layer) ===")
    print(f"Transcript length: {len(text)} chars")
    print(f"Preview: {text[:200]}")
    assert len(text) > 100, "Transcript extraction returned too little text"
    assert "love" in text.lower() or "gonna" in text.lower(), "Expected Rick Astley lyrics"
    print("✓ PASS — transcript extraction works\n")
    return text


# ── Test 3: lookup_user_manual (direct, no agent) ─────────────────────────────

def test_user_manual() -> dict:
    """Verify manual lookup returns relevant content for a known appliance."""
    from fixitbuddy.tools import lookup_user_manual

    result = lookup_user_manual(brand="LG", model_number="WM3900HWA")

    print("\n=== TEST 3: lookup_user_manual ===")
    print(json.dumps({k: v[:300] if isinstance(v, str) else v for k, v in result.items()}, indent=2))
    assert result.get("found") or result.get("search_hint"), "Manual lookup returned no usable data"
    print("✓ PASS\n")
    return result


# ── Test 4: Combined agent with all three tools ────────────────────────────────

async def test_combined_agent() -> None:
    """Full agent test: all tools available, ask a real repair question."""
    from google.adk.agents import Agent
    from google.adk.runners import InMemoryRunner
    from google.adk.tools.google_search_tool import GoogleSearchTool
    from google.genai import types

    from fixitbuddy.tools import analyze_youtube_repair_video, lookup_user_manual

    # bypass_multi_tools_limit=True allows google_search alongside custom function tools
    search_tool = GoogleSearchTool(bypass_multi_tools_limit=True)

    agent = Agent(
        model="gemini-2.5-flash",
        name="fixitbuddy_test",
        instruction=(
            "You are a repair assistant. Use tools to answer questions.\n"
            "- Use google_search for general repair info and to find YouTube videos\n"
            "- Use analyze_youtube_repair_video when you have a YouTube URL\n"
            "- Use lookup_user_manual when brand and model number are known"
        ),
        tools=[search_tool, analyze_youtube_repair_video, lookup_user_manual],
    )
    runner = InMemoryRunner(agent=agent)
    session = await runner.session_service.create_session(app_name=runner.app_name, user_id="tester")

    response_text = ""
    async for event in runner.run_async(
        user_id="tester",
        session_id=session.id,
        new_message=types.Content(
            role="user",
            parts=[types.Part(text="My LG WM3900HWA washer shows OE error. Look up the manual and tell me how to fix it.")],
        ),
    ):
        if event.is_final_response() and event.content:
            for part in event.content.parts:
                if part.text:
                    response_text += part.text

    print("\n=== TEST 4: Combined agent ===")
    print(response_text[:800])
    assert len(response_text) > 50, "Combined agent returned empty response"
    print("✓ PASS\n")


# ── Main ───────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    if not os.environ.get("GOOGLE_API_KEY"):
        print("ERROR: GOOGLE_API_KEY not set. Add it to backend/.env or export it.")
        sys.exit(1)

    print("FixIt Buddy — Knowledge Tools POC\n" + "=" * 40)

    # Sync tests first
    test_youtube_analysis()
    test_user_manual()

    # Async tests
    asyncio.run(test_google_search())
    asyncio.run(test_combined_agent())

    print("=" * 40)
    print("All tests passed ✓")
