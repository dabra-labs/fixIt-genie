"""Unit tests for FixIt Buddy tools."""
import pytest
import sys
import os

# Add parent directory to path to import tools
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from fixitbuddy.tools import lookup_equipment_knowledge, get_safety_warnings, log_diagnostic_step


class TestLookupEquipmentKnowledge:
    """Test lookup_equipment_knowledge function."""

    def test_search_by_keyword_oil(self):
        """Test search by keyword 'oil' returns automotive_oil_system data."""
        result = lookup_equipment_knowledge(query="oil")
        assert result["found"] is True
        assert len(result["results"]) > 0
        assert any(doc["name"] == "Engine Oil System" for doc in result["results"])

    def test_search_by_keyword_battery(self):
        """Test search by keyword 'battery' returns automotive_battery data."""
        result = lookup_equipment_knowledge(query="battery")
        assert result["found"] is True
        assert len(result["results"]) > 0
        assert any(doc["name"] == "Car Battery and Electrical" for doc in result["results"])

    def test_search_by_keyword_coolant(self):
        """Test search by keyword 'coolant' returns automotive_coolant data."""
        result = lookup_equipment_knowledge(query="coolant")
        assert result["found"] is True
        assert len(result["results"]) > 0
        assert any(doc["name"] == "Cooling System" for doc in result["results"])

    def test_search_by_keyword_breaker(self):
        """Test search by keyword 'breaker' returns electrical_breaker_panel data."""
        result = lookup_equipment_knowledge(query="breaker")
        assert result["found"] is True
        assert len(result["results"]) > 0
        assert any(doc["name"] == "Residential Breaker Panel" for doc in result["results"])

    def test_search_by_keyword_gfci(self):
        """Test search by keyword 'gfci' returns electrical_gfci data."""
        result = lookup_equipment_knowledge(query="gfci")
        assert result["found"] is True
        assert len(result["results"]) > 0
        assert any(doc["name"] == "GFCI Outlets" for doc in result["results"])

    def test_search_by_keyword_washing_machine(self):
        """Test search by keyword 'washing machine' returns appliance_washing_machine data."""
        result = lookup_equipment_knowledge(query="washing machine")
        assert result["found"] is True
        assert len(result["results"]) > 0
        assert any(doc["name"] == "Washing Machine" for doc in result["results"])

    def test_search_by_keyword_dishwasher(self):
        """Test search by keyword 'dishwasher' returns appliance_dishwasher data."""
        result = lookup_equipment_knowledge(query="dishwasher")
        assert result["found"] is True
        assert len(result["results"]) > 0
        assert any(doc["name"] == "Dishwasher" for doc in result["results"])

    def test_search_lg_fridge_off_demo_mode(self):
        """Test LG fridge OFF / demo-mode wording returns refrigerator knowledge."""
        result = lookup_equipment_knowledge(
            query="LG fridge display says OFF and it is not cooling",
            category="appliance",
        )
        assert result["found"] is True
        assert len(result["results"]) > 0
        fridge = next(doc for doc in result["results"] if doc["name"] == "LG Refrigerator")
        issue_text = " ".join(issue["issue"] for issue in fridge["common_issues"])
        assert "OFF" in issue_text

    def test_search_lg_fridge_0ff_demo_mode(self):
        """Test LG fridge 0FF wording ranks refrigerator knowledge first."""
        result = lookup_equipment_knowledge(
            query="refrigerator error code 0 FF",
            category="appliance",
            error_code="0 FF",
        )
        assert result["found"] is True
        assert result["results"][0]["name"] == "LG Refrigerator"

    def test_search_lg_fridge_ff_query_prefers_refrigerator(self):
        """Fridge-specific FF queries should not rank generic appliance docs first."""
        result = lookup_equipment_knowledge(
            query="refrigerator leaking water dispenser error code FF",
            category="appliance",
        )
        assert result["found"] is True
        assert result["results"][0]["name"] == "LG Refrigerator"

    def test_search_by_error_code_p0520(self):
        """Test search by error code 'P0520' returns oil system."""
        result = lookup_equipment_knowledge(query="", error_code="P0520")
        assert result["found"] is True
        assert len(result["results"]) > 0
        assert result["results"][0]["name"] == "Engine Oil System"

    def test_search_by_error_code_e4(self):
        """Test search by error code 'E4' returns washing machine."""
        result = lookup_equipment_knowledge(query="", error_code="E4")
        assert result["found"] is True
        assert len(result["results"]) > 0
        # E4 can be in washing machine or dishwasher, check it returns one
        assert any(doc["name"] in ["Washing Machine", "Dishwasher"] for doc in result["results"])

    def test_search_by_error_code_e15(self):
        """Test search by error code 'E15' returns dishwasher."""
        result = lookup_equipment_knowledge(query="", error_code="E15")
        assert result["found"] is True
        assert len(result["results"]) > 0
        assert result["results"][0]["name"] == "Dishwasher"

    def test_search_by_error_code_p0562(self):
        """Test search by error code 'P0562' returns battery."""
        result = lookup_equipment_knowledge(query="", error_code="P0562")
        assert result["found"] is True
        assert len(result["results"]) > 0
        assert result["results"][0]["name"] == "Car Battery and Electrical"

    def test_search_by_category_automotive_with_keyword_oil(self):
        """Test search by category 'automotive' with keyword 'oil' returns correct results."""
        result = lookup_equipment_knowledge(query="oil", category="automotive")
        assert result["found"] is True
        assert len(result["results"]) > 0
        assert all(doc["category"] == "automotive" for doc in result["results"])
        assert any(doc["name"] == "Engine Oil System" for doc in result["results"])

    def test_search_by_category_electrical_with_keyword_breaker(self):
        """Test search by category 'electrical' with keyword 'breaker' returns correct results."""
        result = lookup_equipment_knowledge(query="breaker", category="electrical")
        assert result["found"] is True
        assert len(result["results"]) > 0
        assert all(doc["category"] == "electrical" for doc in result["results"])
        assert any(doc["name"] == "Residential Breaker Panel" for doc in result["results"])

    def test_search_by_category_appliance_with_keyword_dishwasher(self):
        """Test search by category 'appliance' with keyword 'dishwasher' returns correct results."""
        result = lookup_equipment_knowledge(query="dishwasher", category="appliance")
        assert result["found"] is True
        assert len(result["results"]) > 0
        assert all(doc["category"] == "appliance" for doc in result["results"])
        assert any(doc["name"] == "Dishwasher" for doc in result["results"])

    def test_search_with_unknown_query(self):
        """Test search with unknown query returns found=False."""
        result = lookup_equipment_knowledge(query="xyzabc123notarealthing")
        assert result["found"] is False

    def test_search_with_unknown_error_code(self):
        """Test search with unknown error code returns found=False."""
        result = lookup_equipment_knowledge(query="", error_code="Z9999")
        assert result["found"] is False

    def test_results_limited_to_max_3(self):
        """Test results are limited to max 3 results."""
        result = lookup_equipment_knowledge(query="error")
        if result["found"]:
            assert len(result["results"]) <= 3

    def test_returned_results_have_required_keys(self):
        """Test each returned result has required keys."""
        result = lookup_equipment_knowledge(query="oil")
        assert result["found"] is True
        assert len(result["results"]) > 0

        required_keys = {"category", "name", "diagnostic_steps", "safety_notes"}
        for doc in result["results"]:
            assert required_keys.issubset(doc.keys()), f"Missing keys in {doc}"


class TestGetSafetyWarnings:
    """Test get_safety_warnings function."""

    def test_electrical_warnings(self):
        """Test electrical action_type returns correct warnings mentioning safety terms."""
        result = get_safety_warnings(action_type="electrical")
        assert "warnings" in result
        assert "general" in result
        assert isinstance(result["warnings"], list)
        assert len(result["warnings"]) > 0
        # Check for relevant safety keywords
        all_text = " ".join(result["warnings"]).lower()
        assert any(term in all_text for term in ["power", "electrocution", "voltage"])

    def test_mechanical_warnings(self):
        """Test mechanical action_type returns correct warnings."""
        result = get_safety_warnings(action_type="mechanical")
        assert "warnings" in result
        assert isinstance(result["warnings"], list)
        assert len(result["warnings"]) > 0

    def test_fluid_warnings(self):
        """Test fluid action_type returns correct warnings."""
        result = get_safety_warnings(action_type="fluid")
        assert "warnings" in result
        assert isinstance(result["warnings"], list)
        assert len(result["warnings"]) > 0

    def test_pressure_warnings(self):
        """Test pressure action_type returns correct warnings."""
        result = get_safety_warnings(action_type="pressure")
        assert "warnings" in result
        assert isinstance(result["warnings"], list)
        assert len(result["warnings"]) > 0

    def test_heat_warnings(self):
        """Test heat action_type returns correct warnings."""
        result = get_safety_warnings(action_type="heat")
        assert "warnings" in result
        assert isinstance(result["warnings"], list)
        assert len(result["warnings"]) > 0
        # Check for relevant safety keywords
        all_text = " ".join(result["warnings"]).lower()
        assert any(term in all_text for term in ["hot", "cool", "thermal"])

    def test_chemical_warnings(self):
        """Test chemical action_type returns correct warnings."""
        result = get_safety_warnings(action_type="chemical")
        assert "warnings" in result
        assert isinstance(result["warnings"], list)
        assert len(result["warnings"]) > 0
        # Check for relevant safety keywords
        all_text = " ".join(result["warnings"]).lower()
        assert any(term in all_text for term in ["ventilated", "gloves"])

    def test_unknown_action_type_returns_fallback(self):
        """Test unknown action_type returns general fallback warnings."""
        result = get_safety_warnings(action_type="unknown_action_xyz")
        assert "warnings" in result
        assert isinstance(result["warnings"], list)
        assert len(result["warnings"]) > 0

    def test_response_has_required_structure(self):
        """Test response always has 'warnings' list and 'general' message."""
        result = get_safety_warnings(action_type="electrical")
        assert "warnings" in result
        assert "general" in result
        assert isinstance(result["warnings"], list)
        assert isinstance(result["general"], str)
        assert len(result["general"]) > 0

    def test_general_message_present(self):
        """Test general safety message is always present."""
        result = get_safety_warnings(action_type="mechanical")
        assert "general" in result
        assert "safety" in result["general"].lower()


class TestLogDiagnosticStep:
    """Test log_diagnostic_step function."""

    def test_returns_logged_true(self):
        """Test function returns logged=True."""
        result = log_diagnostic_step(1, "Check oil level")
        assert result["logged"] is True

    def test_returned_step_has_required_fields(self):
        """Test returned step contains required fields."""
        result = log_diagnostic_step(1, "Check oil level", "Oil is low", "Need to add oil")
        assert "step" in result
        step = result["step"]
        assert "step" in step
        assert "description" in step
        assert "observation" in step
        assert "result" in step

    def test_handles_empty_optional_params(self):
        """Test handles empty strings for optional params."""
        result = log_diagnostic_step(2, "Check battery", "", "")
        assert result["logged"] is True
        step = result["step"]
        assert step["observation"] == ""
        assert step["result"] == ""

    def test_step_number_preserved(self):
        """Test step number is preserved correctly."""
        for i in range(1, 6):
            result = log_diagnostic_step(i, f"Step {i}")
            assert result["step"]["step"] == i

    def test_description_preserved(self):
        """Test description is preserved correctly."""
        desc = "Check the oil dipstick reading"
        result = log_diagnostic_step(1, desc)
        assert result["step"]["description"] == desc

    def test_observation_preserved(self):
        """Test observation is preserved correctly."""
        obs = "Oil level is between MIN and MAX"
        result = log_diagnostic_step(1, "Check oil", observation=obs)
        assert result["step"]["observation"] == obs

    def test_result_preserved(self):
        """Test result is preserved correctly."""
        res = "Oil level is acceptable"
        result = log_diagnostic_step(1, "Check oil", result=res)
        assert result["step"]["result"] == res

    def test_full_step_logging(self):
        """Test complete step logging with all parameters."""
        result = log_diagnostic_step(
            step_number=3,
            description="Inspect battery terminals",
            observation="Found white corrosion on positive terminal",
            result="Terminal cleaned with baking soda solution"
        )
        assert result["logged"] is True
        step = result["step"]
        assert step["step"] == 3
        assert step["description"] == "Inspect battery terminals"
        assert step["observation"] == "Found white corrosion on positive terminal"
        assert step["result"] == "Terminal cleaned with baking soda solution"

    def test_missing_step_number_defaults_to_zero(self):
        """Model tool calls sometimes omit step_number; the tool should still succeed."""
        result = log_diagnostic_step(description="Observed fridge display")
        assert result["logged"] is True
        assert result["step"]["step"] == 0
