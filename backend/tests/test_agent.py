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

    def test_system_instruction_mentions_partial_ff_ambiguity(self):
        """The prompt should tell the agent not to overconfidently guess from FF alone."""
        assert "partial fridge display like FF" in SYSTEM_INSTRUCTION
        assert "OFF/0FF demo mode" in SYSTEM_INSTRUCTION

    def test_system_instruction_mentions_camera_guidance_and_investigation_narration(self):
        """The agent should direct camera framing and narrate when it is checking something."""
        assert "tell the user where to point the camera" in SYSTEM_INSTRUCTION
        assert "Hold that there, I'm reading the display" in SYSTEM_INSTRUCTION
        assert "checking that code" in SYSTEM_INSTRUCTION

    def test_agent_description_non_empty(self):
        """Test agent description is non-empty."""
        assert agent.description is not None
        assert len(agent.description) > 0

    def test_tools_are_invocable_or_adk_tools(self):
        """Test each tool is either a function or an ADK tool object."""
        for tool in agent.tools:
            assert callable(tool) or isinstance(tool, GoogleSearchTool)

    def test_agent_has_telemetry_callbacks(self):
        """Test structured telemetry callbacks are wired into the agent."""
        assert agent.before_agent_callback is not None
        assert agent.after_agent_callback is not None
        assert agent.before_model_callback is not None
        assert agent.after_model_callback is not None
        assert agent.before_tool_callback is not None
        assert agent.after_tool_callback is not None
        assert agent.on_model_error_callback is not None
        assert agent.on_tool_error_callback is not None
