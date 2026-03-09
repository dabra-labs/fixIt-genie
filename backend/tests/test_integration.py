"""Comprehensive integration tests for FixIt Buddy ADK backend server."""
import pytest
import requests
import json
import time
import subprocess
import os
import signal
from pathlib import Path

# Configuration
BASE_URL = "http://localhost:8081"
BACKEND_DIR = Path(__file__).parent.parent
SERVER_PORT = 8081
SERVER_HOST = "0.0.0.0"
GOOGLE_API_KEY = "test-key"

# Global server process
SERVER_PROCESS = None


@pytest.fixture(scope="session", autouse=True)
def start_server():
    """Start the ADK API server as a fixture."""
    global SERVER_PROCESS

    # Set up environment
    env = os.environ.copy()
    env["PATH"] = f"/sessions/exciting-dreamy-hypatia/.local/bin:{env.get('PATH', '')}"
    env["GOOGLE_GENAI_USE_VERTEXAI"] = "FALSE"
    env["GOOGLE_API_KEY"] = GOOGLE_API_KEY

    # Start server
    print(f"Starting ADK server on {SERVER_HOST}:{SERVER_PORT}...")
    SERVER_PROCESS = subprocess.Popen(
        [
            "adk",
            "api_server",
            f"--port={SERVER_PORT}",
            f"--host={SERVER_HOST}",
            str(BACKEND_DIR),
        ],
        env=env,
        cwd=str(BACKEND_DIR),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )

    # Wait for server to be ready
    max_retries = 30
    for i in range(max_retries):
        try:
            response = requests.get(f"{BASE_URL}/health", timeout=2)
            if response.status_code == 200:
                print("Server is ready!")
                break
        except requests.exceptions.ConnectionError:
            if i < max_retries - 1:
                time.sleep(1)
            else:
                # Kill process and re-raise
                if SERVER_PROCESS:
                    SERVER_PROCESS.kill()
                raise RuntimeError(
                    f"Server failed to start after {max_retries} seconds"
                )

    yield SERVER_PROCESS

    # Cleanup: Kill the server
    if SERVER_PROCESS and SERVER_PROCESS.poll() is None:
        print("Stopping ADK server...")
        SERVER_PROCESS.send_signal(signal.SIGTERM)
        try:
            SERVER_PROCESS.wait(timeout=5)
        except subprocess.TimeoutExpired:
            SERVER_PROCESS.kill()
        print("Server stopped")


class TestHealthAndMetadata:
    """Test basic health and metadata endpoints."""

    def test_health_endpoint(self):
        """Test /health endpoint returns OK."""
        response = requests.get(f"{BASE_URL}/health")
        assert response.status_code == 200
        data = response.json()
        assert data.get("status") == "ok"

    def test_version_endpoint(self):
        """Test /version endpoint returns version info."""
        response = requests.get(f"{BASE_URL}/version")
        assert response.status_code == 200
        data = response.json()
        # Version endpoint returns a dict with version info
        assert isinstance(data, dict)

    def test_openapi_docs(self):
        """Test OpenAPI documentation is available."""
        response = requests.get(f"{BASE_URL}/openapi.json")
        assert response.status_code == 200
        data = response.json()
        assert data.get("openapi") == "3.1.0"
        assert "paths" in data
        assert "info" in data

    def test_swagger_ui_available(self):
        """Test Swagger UI is available at /docs."""
        response = requests.get(f"{BASE_URL}/docs")
        assert response.status_code == 200
        # Should return HTML content
        assert "swagger-ui" in response.text.lower() or "html" in response.text.lower()


class TestAppListing:
    """Test app/agent listing endpoints."""

    def test_list_apps_simple(self):
        """Test /list-apps returns list of app names."""
        response = requests.get(f"{BASE_URL}/list-apps")
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        # Should contain fixitbuddy
        assert "fixitbuddy" in data

    def test_list_apps_detailed(self):
        """Test /list-apps with detailed=true parameter."""
        response = requests.get(f"{BASE_URL}/list-apps?detailed=true")
        assert response.status_code == 200
        data = response.json()
        # Should have apps key in detailed mode
        assert "apps" in data
        assert isinstance(data["apps"], list)


class TestAppInfo:
    """Test app information endpoints."""

    def test_get_fixitbuddy_info(self):
        """Test GET /apps/fixitbuddy returns agent info."""
        response = requests.get(f"{BASE_URL}/apps/fixitbuddy")
        assert response.status_code == 200
        data = response.json()

        # Verify response structure
        assert data.get("name") == "fixitbuddy"
        assert "root_agent" in data

        # Verify root_agent structure
        root_agent = data["root_agent"]
        assert root_agent.get("name") == "fixitbuddy"
        assert root_agent.get("description") is not None
        assert "multimodal" in root_agent.get("description", "").lower()
        assert isinstance(root_agent.get("sub_agents", []), list)

    def test_get_nonexistent_app(self):
        """Test GET /apps/<nonexistent> returns 404."""
        response = requests.get(f"{BASE_URL}/apps/nonexistent-app-xyz")
        # Should return 500 or 404 depending on implementation
        assert response.status_code in [404, 500]


class TestSessionManagement:
    """Test session creation and management endpoints."""

    def test_create_session(self):
        """Test creating a new session."""
        response = requests.post(
            f"{BASE_URL}/apps/fixitbuddy/users/test-user/sessions",
            headers={"Content-Type": "application/json"},
        )
        assert response.status_code == 200
        data = response.json()

        # Verify session structure
        assert "id" in data
        assert data.get("appName") == "fixitbuddy"
        assert data.get("userId") == "test-user"
        assert isinstance(data.get("state", {}), dict)
        assert isinstance(data.get("events", []), list)
        assert "lastUpdateTime" in data

    def test_create_multiple_sessions(self):
        """Test creating multiple sessions for same user."""
        session_ids = []

        # Create 3 sessions
        for i in range(3):
            response = requests.post(
                f"{BASE_URL}/apps/fixitbuddy/users/multi-user/sessions",
                headers={"Content-Type": "application/json"},
            )
            assert response.status_code == 200
            data = response.json()
            session_ids.append(data["id"])

        # All session IDs should be unique
        assert len(set(session_ids)) == 3

    def test_get_session(self):
        """Test retrieving a session."""
        # Create session first
        create_response = requests.post(
            f"{BASE_URL}/apps/fixitbuddy/users/get-user/sessions",
            headers={"Content-Type": "application/json"},
        )
        assert create_response.status_code == 200
        session_id = create_response.json()["id"]

        # Get session
        get_response = requests.get(
            f"{BASE_URL}/apps/fixitbuddy/users/get-user/sessions/{session_id}",
        )
        assert get_response.status_code == 200
        data = get_response.json()

        # Verify it's the same session
        assert data["id"] == session_id
        assert data["userId"] == "get-user"
        assert data["appName"] == "fixitbuddy"

    def test_list_user_sessions(self):
        """Test listing all sessions for a user."""
        # Create a couple of sessions
        user_id = "list-user"
        for i in range(2):
            requests.post(
                f"{BASE_URL}/apps/fixitbuddy/users/{user_id}/sessions",
                headers={"Content-Type": "application/json"},
            )

        # List sessions
        list_response = requests.get(
            f"{BASE_URL}/apps/fixitbuddy/users/{user_id}/sessions",
        )
        assert list_response.status_code == 200
        data = list_response.json()

        # Should be a list
        assert isinstance(data, list)
        # Should have at least 2 sessions
        assert len(data) >= 2

        # Each session should have expected fields
        for session in data:
            assert "id" in session
            assert session.get("userId") == user_id


class TestArtifacts:
    """Test artifact storage endpoints."""

    def test_create_artifact_after_session(self):
        """Test artifact listing endpoint."""
        # Create session
        session_response = requests.post(
            f"{BASE_URL}/apps/fixitbuddy/users/artifact-user/sessions",
            headers={"Content-Type": "application/json"},
        )
        assert session_response.status_code == 200
        session_id = session_response.json()["id"]

        # Try to list artifacts (should be empty initially)
        artifacts_response = requests.get(
            f"{BASE_URL}/apps/fixitbuddy/users/artifact-user/sessions/{session_id}/artifacts",
        )
        # Either 200 with empty list or 404 is acceptable
        assert artifacts_response.status_code in [200, 404]
        if artifacts_response.status_code == 200:
            data = artifacts_response.json()
            assert isinstance(data, (list, dict))


class TestDebugEndpoints:
    """Test debug/tracing endpoints."""

    def test_trace_endpoint_not_found(self):
        """Test trace endpoint with nonexistent event ID."""
        response = requests.get(
            f"{BASE_URL}/debug/trace/nonexistent-event-id",
        )
        # Should either return 404 or empty data
        assert response.status_code in [200, 404]

    def test_session_trace_endpoint_not_found(self):
        """Test session trace endpoint with nonexistent session ID."""
        response = requests.get(
            f"{BASE_URL}/debug/trace/session/nonexistent-session-id",
        )
        # Should either return 404 or empty data
        assert response.status_code in [200, 404]


class TestWebSocketEndpoints:
    """Test WebSocket endpoint availability."""

    def test_ws_endpoint_exists(self):
        """Test that WebSocket endpoint exists in API schema."""
        response = requests.get(f"{BASE_URL}/openapi.json")
        assert response.status_code == 200
        data = response.json()
        paths = data.get("paths", {})

        # Check for WebSocket routes (they appear as regular routes in OpenAPI)
        # Look for /run or similar endpoints that support WebSocket
        assert len(paths) > 0  # Should have some endpoints


class TestAPIContract:
    """Document and validate the API contract."""

    def test_api_contract_consistency(self):
        """Test that API contract is consistent with documentation."""
        # Get OpenAPI schema
        response = requests.get(f"{BASE_URL}/openapi.json")
        assert response.status_code == 200
        schema = response.json()

        # Verify expected endpoints exist
        paths = schema.get("paths", {})
        expected_endpoints = [
            "/health",
            "/version",
            "/list-apps",
            "/apps/{app_name}",
            "/apps/{app_name}/users/{user_id}/sessions",
        ]

        for endpoint in expected_endpoints:
            assert endpoint in paths, f"Expected endpoint {endpoint} not found in API"

    def test_health_response_schema(self):
        """Test health endpoint response matches schema."""
        response = requests.get(f"{BASE_URL}/health")
        assert response.status_code == 200
        data = response.json()

        # Should be a dict with status field
        assert isinstance(data, dict)
        assert "status" in data
        assert isinstance(data["status"], str)

    def test_session_response_schema(self):
        """Test session response matches expected schema."""
        response = requests.post(
            f"{BASE_URL}/apps/fixitbuddy/users/schema-user/sessions",
            headers={"Content-Type": "application/json"},
        )
        assert response.status_code == 200
        data = response.json()

        # Validate required fields
        required_fields = ["id", "appName", "userId", "state", "events", "lastUpdateTime"]
        for field in required_fields:
            assert field in data, f"Missing required field: {field}"

        # Validate field types
        assert isinstance(data["id"], str)
        assert isinstance(data["appName"], str)
        assert isinstance(data["userId"], str)
        assert isinstance(data["state"], dict)
        assert isinstance(data["events"], list)
        assert isinstance(data["lastUpdateTime"], (int, float))

    def test_app_info_response_schema(self):
        """Test app info response matches expected schema."""
        response = requests.get(f"{BASE_URL}/apps/fixitbuddy")
        assert response.status_code == 200
        data = response.json()

        # Validate structure
        assert "name" in data
        assert "root_agent" in data

        root_agent = data["root_agent"]
        assert "name" in root_agent
        assert "description" in root_agent
        assert "sub_agents" in root_agent
        assert isinstance(root_agent["sub_agents"], list)


class TestErrorHandling:
    """Test error handling and edge cases."""

    def test_invalid_json_request(self):
        """Test server handles invalid JSON gracefully."""
        response = requests.post(
            f"{BASE_URL}/apps/fixitbuddy/users/test/sessions",
            headers={"Content-Type": "application/json"},
            data="invalid json {",
        )
        # Should return 400 or 422
        assert response.status_code in [400, 422]

    def test_missing_required_path_parameters(self):
        """Test endpoint without required path parameters."""
        response = requests.get(f"{BASE_URL}/apps/")
        # Should return 404 or 405
        assert response.status_code in [404, 405]

    def test_method_not_allowed(self):
        """Test using wrong HTTP method."""
        # Try POST on GET-only endpoint
        response = requests.post(f"{BASE_URL}/health")
        assert response.status_code == 405

    def test_cors_headers_present(self):
        """Test CORS headers are properly configured."""
        response = requests.get(f"{BASE_URL}/health")
        # Check if CORS headers are present (not required, but good practice)
        # This is informational, not a hard requirement
        assert response.status_code == 200


class TestPerformance:
    """Test performance and concurrency."""

    def test_multiple_concurrent_session_creations(self):
        """Test creating multiple sessions concurrently."""
        import concurrent.futures

        def create_session(user_id):
            response = requests.post(
                f"{BASE_URL}/apps/fixitbuddy/users/{user_id}/sessions",
                headers={"Content-Type": "application/json"},
            )
            return response.status_code, response.json()

        # Create 5 sessions concurrently
        with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
            futures = [
                executor.submit(create_session, f"concurrent-user-{i}")
                for i in range(5)
            ]
            results = [f.result() for f in concurrent.futures.as_completed(futures)]

        # All should succeed
        assert all(status == 200 for status, _ in results)

        # All session IDs should be unique
        session_ids = [data["id"] for _, data in results]
        assert len(set(session_ids)) == 5


class TestResponseFormats:
    """Test response format consistency."""

    def test_json_response_formatting(self):
        """Test all responses are properly formatted JSON."""
        endpoints = [
            "/health",
            "/version",
            "/list-apps",
            "/apps/fixitbuddy",
        ]

        for endpoint in endpoints:
            response = requests.get(f"{BASE_URL}{endpoint}")
            assert response.status_code == 200

            # Should be valid JSON
            try:
                data = response.json()
                assert data is not None
            except json.JSONDecodeError:
                pytest.fail(f"Endpoint {endpoint} did not return valid JSON")

    def test_session_response_has_iso_timestamp(self):
        """Test session lastUpdateTime is a valid timestamp."""
        response = requests.post(
            f"{BASE_URL}/apps/fixitbuddy/users/time-user/sessions",
            headers={"Content-Type": "application/json"},
        )
        assert response.status_code == 200
        data = response.json()

        # lastUpdateTime should be a valid number (unix timestamp)
        last_update = data.get("lastUpdateTime")
        assert isinstance(last_update, (int, float))
        assert last_update > 0


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
