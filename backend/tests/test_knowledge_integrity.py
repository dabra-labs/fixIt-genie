"""Data integrity tests for FixIt Buddy knowledge base."""
import pytest
import sys
import os

# Add parent directory to path to import tools
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from fixitbuddy.tools import _KNOWLEDGE_BASE


class TestKnowledgeBaseStructure:
    """Test knowledge base structure and integrity."""

    def test_all_7_knowledge_documents_exist(self):
        """Test all 7 knowledge base documents exist in _KNOWLEDGE_BASE."""
        expected_docs = [
            "automotive_oil_system",
            "automotive_battery",
            "automotive_coolant",
            "electrical_breaker_panel",
            "electrical_gfci",
            "appliance_washing_machine",
            "appliance_dishwasher"
        ]
        for doc_id in expected_docs:
            assert doc_id in _KNOWLEDGE_BASE, f"Missing document: {doc_id}"

    def test_knowledge_base_has_7_documents(self):
        """Test knowledge base has exactly 7 documents."""
        assert len(_KNOWLEDGE_BASE) == 7

    def test_every_document_has_required_fields(self):
        """Test every document has all required fields."""
        required_fields = {
            "category",
            "name",
            "description",
            "error_codes",
            "keywords",
            "diagnostic_steps",
            "common_issues",
            "safety_notes"
        }
        for doc_id, doc in _KNOWLEDGE_BASE.items():
            missing = required_fields - set(doc.keys())
            assert not missing, f"Document {doc_id} missing fields: {missing}"

    def test_diagnostic_steps_have_at_least_3_steps(self):
        """Test every diagnostic_steps list has at least 3 steps."""
        for doc_id, doc in _KNOWLEDGE_BASE.items():
            steps = doc.get("diagnostic_steps", [])
            assert len(steps) >= 3, f"Document {doc_id} has fewer than 3 diagnostic steps"

    def test_diagnostic_steps_have_required_keys(self):
        """Test every step has 'step', 'instruction', and 'visual_cue' keys."""
        required_step_keys = {"step", "instruction", "visual_cue"}
        for doc_id, doc in _KNOWLEDGE_BASE.items():
            steps = doc.get("diagnostic_steps", [])
            for i, step in enumerate(steps):
                missing = required_step_keys - set(step.keys())
                assert not missing, f"Document {doc_id} step {i} missing keys: {missing}"

    def test_common_issues_have_required_keys(self):
        """Test every common_issues entry has 'issue', 'cause', 'fix' keys."""
        required_issue_keys = {"issue", "cause", "fix"}
        for doc_id, doc in _KNOWLEDGE_BASE.items():
            issues = doc.get("common_issues", [])
            for i, issue in enumerate(issues):
                missing = required_issue_keys - set(issue.keys())
                assert not missing, f"Document {doc_id} issue {i} missing keys: {missing}"

    def test_safety_notes_non_empty_list(self):
        """Test safety_notes is always a non-empty list."""
        for doc_id, doc in _KNOWLEDGE_BASE.items():
            notes = doc.get("safety_notes")
            assert isinstance(notes, list), f"Document {doc_id} safety_notes is not a list"
            assert len(notes) > 0, f"Document {doc_id} safety_notes is empty"

    def test_keywords_non_empty_list(self):
        """Test keywords is always a non-empty list."""
        for doc_id, doc in _KNOWLEDGE_BASE.items():
            keywords = doc.get("keywords")
            assert isinstance(keywords, list), f"Document {doc_id} keywords is not a list"
            assert len(keywords) > 0, f"Document {doc_id} keywords is empty"

    def test_category_valid_values(self):
        """Test category is one of: automotive, electrical, appliance."""
        valid_categories = {"automotive", "electrical", "appliance"}
        for doc_id, doc in _KNOWLEDGE_BASE.items():
            category = doc.get("category")
            assert category in valid_categories, \
                f"Document {doc_id} has invalid category: {category}"

    def test_count_documents_by_category(self):
        """Test there are exactly 3 automotive docs, 2 electrical docs, 2 appliance docs."""
        categories = {}
        for doc_id, doc in _KNOWLEDGE_BASE.items():
            cat = doc.get("category")
            if cat not in categories:
                categories[cat] = 0
            categories[cat] += 1

        assert categories.get("automotive") == 3, \
            f"Expected 3 automotive docs, got {categories.get('automotive')}"
        assert categories.get("electrical") == 2, \
            f"Expected 2 electrical docs, got {categories.get('electrical')}"
        assert categories.get("appliance") == 2, \
            f"Expected 2 appliance docs, got {categories.get('appliance')}"

    def test_total_error_codes_at_least_30(self):
        """Test total error codes across all docs >= 30."""
        total_codes = 0
        for doc_id, doc in _KNOWLEDGE_BASE.items():
            codes = doc.get("error_codes", [])
            total_codes += len(codes)

        assert total_codes >= 30, \
            f"Expected at least 30 error codes total, got {total_codes}"

    def test_all_error_codes_are_strings(self):
        """Test all error codes are strings."""
        for doc_id, doc in _KNOWLEDGE_BASE.items():
            codes = doc.get("error_codes", [])
            for code in codes:
                assert isinstance(code, str), \
                    f"Document {doc_id} has non-string error code: {code}"

    def test_specific_automotive_documents(self):
        """Test the 3 automotive documents are the expected ones."""
        automotive_docs = {
            "automotive_oil_system",
            "automotive_battery",
            "automotive_coolant"
        }
        for doc_id in automotive_docs:
            assert doc_id in _KNOWLEDGE_BASE
            assert _KNOWLEDGE_BASE[doc_id]["category"] == "automotive"

    def test_specific_electrical_documents(self):
        """Test the 2 electrical documents are the expected ones."""
        electrical_docs = {
            "electrical_breaker_panel",
            "electrical_gfci"
        }
        for doc_id in electrical_docs:
            assert doc_id in _KNOWLEDGE_BASE
            assert _KNOWLEDGE_BASE[doc_id]["category"] == "electrical"

    def test_specific_appliance_documents(self):
        """Test the 2 appliance documents are the expected ones."""
        appliance_docs = {
            "appliance_washing_machine",
            "appliance_dishwasher"
        }
        for doc_id in appliance_docs:
            assert doc_id in _KNOWLEDGE_BASE
            assert _KNOWLEDGE_BASE[doc_id]["category"] == "appliance"

    def test_error_codes_are_lists(self):
        """Test error_codes is always a list."""
        for doc_id, doc in _KNOWLEDGE_BASE.items():
            codes = doc.get("error_codes")
            assert isinstance(codes, list), \
                f"Document {doc_id} error_codes is not a list"

    def test_common_issues_non_empty(self):
        """Test common_issues is a non-empty list."""
        for doc_id, doc in _KNOWLEDGE_BASE.items():
            issues = doc.get("common_issues")
            assert isinstance(issues, list), \
                f"Document {doc_id} common_issues is not a list"
            assert len(issues) > 0, \
                f"Document {doc_id} common_issues is empty"
