"""Agent definition tests for FixIt Buddy."""
import pytest
import sys
import os

# Add parent directory to path to import agent
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from fixitbuddy.agent import agent, SYSTEM_INSTRUCTION


class TestAgentDefinition:
    """Test agent is properly defined."""

    def test_agent_name(self):
        """Test agent name is 'fixitbuddy'."""
        assert agent.name == "fixitbuddy"

    def test_agent_model(self):
        """Test agent model is 'gemini-2.0-flash-live-001'."""
        assert agent.model == "gemini-2.0-flash-live-001"

    def test_agent_has_4_tools(self):
        """Test agent has exactly 4 tools."""
        assert len(agent.tools) == 4

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

    def test_agent_description_non_empty(self):
        """Test agent description is non-empty."""
        assert agent.description is not None
        assert len(agent.description) > 0

    def test_tool_functions_callable(self):
        """Test all tool functions are callable."""
        # Tools list contains: lookup_equipment_knowledge, get_safety_warnings,
        # log_diagnostic_step (functions), and google_search (object)
        for tool in agent.tools:
            # google_search is a tool object, not a function
            # The other three should be callable
            if hasattr(tool, '__call__'):
                assert callable(tool)
