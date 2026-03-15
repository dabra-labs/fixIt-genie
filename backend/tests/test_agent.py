"""Agent definition tests for FixIt Genie."""
import os
import sys

from google.adk.tools.google_search_tool import GoogleSearchTool

# Add parent directory to path to import agent
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from fixitbuddy.agent import agent, SYSTEM_INSTRUCTION


class TestAgentDefinition:
    """Test agent is properly defined."""

    def test_agent_name(self):
        """Test agent name matches the current product identity."""
        assert agent.name == "fixitgenie"

    def test_agent_model(self):
        """Test agent model defaults to native audio preview for live streaming."""
        # Default model for production live streaming (bidiGenerateContent)
        # Can be overridden via AGENT_MODEL env var for text testing
        assert "gemini" in agent.model

    def test_agent_has_expected_tools(self):
        """Test agent exposes the core repair tools plus web search fallback."""
        tool_names = [getattr(tool, "__name__", type(tool).__name__) for tool in agent.tools]
        assert tool_names == [
            "lookup_equipment_knowledge",
            "get_safety_warnings",
            "log_diagnostic_step",
            "GoogleSearchTool",
        ]
        assert any(isinstance(tool, GoogleSearchTool) for tool in agent.tools)

    def test_agent_instruction_non_empty(self):
        """Test agent has non-empty instruction/system prompt."""
        assert agent.instruction is not None
        assert len(agent.instruction) > 0

    def test_system_instruction_contains_safety(self):
        """Test system instruction contains 'SAFETY'."""
        assert "SAFETY" in SYSTEM_INSTRUCTION

    def test_system_instruction_contains_never(self):
        """Test system instruction contains 'NEVER'."""
        assert "NEVER" in SYSTEM_INSTRUCTION

    def test_system_instruction_mentions_lookup_equipment_knowledge(self):
        """Test system instruction mentions lookup_equipment_knowledge."""
        assert "lookup_equipment_knowledge" in SYSTEM_INSTRUCTION

    def test_system_instruction_mentions_get_safety_warnings(self):
        """Test system instruction mentions get_safety_warnings."""
        assert "get_safety_warnings" in SYSTEM_INSTRUCTION

    def test_system_instruction_mentions_google_search(self):
        """Test system instruction mentions google_search."""
        assert "google_search" in SYSTEM_INSTRUCTION

    def test_agent_description_non_empty(self):
        """Test agent description is non-empty."""
        assert agent.description is not None
        assert len(agent.description) > 0

    def test_tools_are_invocable_or_adk_tools(self):
        """Test each tool is either a function or an ADK tool object."""
        for tool in agent.tools:
            assert callable(tool) or isinstance(tool, GoogleSearchTool)
