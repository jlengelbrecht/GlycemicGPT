"""Story 3.2: Tests for Dexcom CGM data ingestion."""

import uuid
from datetime import UTC, datetime
from unittest.mock import MagicMock, patch

from httpx import ASGITransport, AsyncClient

from src.config import settings
from src.main import app
from src.models.glucose import TrendDirection
from src.services.dexcom_sync import map_trend


def unique_email(prefix: str = "test") -> str:
    """Generate a unique email for testing."""
    return f"{prefix}_{uuid.uuid4().hex[:8]}@example.com"


class TestTrendMapping:
    """Tests for pydexcom trend value mapping."""

    def test_map_string_trends(self):
        """Test mapping string trend values."""
        assert map_trend("flat") == TrendDirection.FLAT
        assert map_trend("singleUp") == TrendDirection.SINGLE_UP
        assert map_trend("singleDown") == TrendDirection.SINGLE_DOWN
        assert map_trend("doubleUp") == TrendDirection.DOUBLE_UP
        assert map_trend("doubleDown") == TrendDirection.DOUBLE_DOWN
        assert map_trend("fortyFiveUp") == TrendDirection.FORTY_FIVE_UP
        assert map_trend("fortyFiveDown") == TrendDirection.FORTY_FIVE_DOWN

    def test_map_numeric_trends(self):
        """Test mapping numeric trend values."""
        assert map_trend(4) == TrendDirection.FLAT
        assert map_trend(2) == TrendDirection.SINGLE_UP
        assert map_trend(6) == TrendDirection.SINGLE_DOWN

    def test_map_unknown_trend(self):
        """Test that unknown trends map to NOT_COMPUTABLE."""
        assert map_trend("unknown") == TrendDirection.NOT_COMPUTABLE
        assert map_trend(999) == TrendDirection.NOT_COMPUTABLE


class TestGlucoseEndpoints:
    """Tests for glucose data endpoints."""

    async def test_get_current_glucose_requires_auth(self):
        """Test that current glucose endpoint requires authentication."""
        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            response = await client.get("/api/integrations/glucose/current")

        assert response.status_code == 401

    async def test_get_glucose_history_requires_auth(self):
        """Test that glucose history endpoint requires authentication."""
        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            response = await client.get("/api/integrations/glucose/history")

        assert response.status_code == 401

    async def test_sync_dexcom_requires_auth(self):
        """Test that Dexcom sync endpoint requires authentication."""
        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            response = await client.post("/api/integrations/dexcom/sync")

        assert response.status_code == 401

    async def test_get_current_glucose_no_readings(self):
        """Test current glucose returns 404 when no readings available."""
        email = unique_email("glucose_empty")
        password = "SecurePass123"

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            # Register and login
            await client.post(
                "/api/auth/register",
                json={"email": email, "password": password},
            )

            login_response = await client.post(
                "/api/auth/login",
                json={"email": email, "password": password},
            )

            session_cookie = login_response.cookies.get(settings.jwt_cookie_name)

            # Get current glucose (should be 404)
            response = await client.get(
                "/api/integrations/glucose/current",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 404
        assert "No glucose readings available" in response.json()["detail"]

    async def test_get_sync_status_not_configured(self):
        """Test sync status when Dexcom is not configured."""
        email = unique_email("sync_status")
        password = "SecurePass123"

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            # Register and login
            await client.post(
                "/api/auth/register",
                json={"email": email, "password": password},
            )

            login_response = await client.post(
                "/api/auth/login",
                json={"email": email, "password": password},
            )

            session_cookie = login_response.cookies.get(settings.jwt_cookie_name)

            # Get sync status
            response = await client.get(
                "/api/integrations/dexcom/sync/status",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200
        data = response.json()
        assert data["integration_status"] == "not_configured"
        assert data["readings_available"] == 0

    async def test_sync_dexcom_not_configured(self):
        """Test sync fails when Dexcom is not configured."""
        email = unique_email("sync_nocred")
        password = "SecurePass123"

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            # Register and login
            await client.post(
                "/api/auth/register",
                json={"email": email, "password": password},
            )

            login_response = await client.post(
                "/api/auth/login",
                json={"email": email, "password": password},
            )

            session_cookie = login_response.cookies.get(settings.jwt_cookie_name)

            # Try to sync
            response = await client.post(
                "/api/integrations/dexcom/sync",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 404
        assert "not configured" in response.json()["detail"].lower()

    @patch("src.services.dexcom_sync.Dexcom")
    @patch("src.routers.integrations.validate_dexcom_credentials")
    async def test_sync_dexcom_with_mocked_data(self, mock_validate, mock_dexcom_class):
        """Test Dexcom sync with mocked Dexcom API."""
        mock_validate.return_value = (True, None)

        # Create mock reading
        mock_reading = MagicMock()
        mock_reading.value = 120
        mock_reading.datetime = datetime.now(UTC)
        mock_reading.trend = 4  # Flat
        mock_reading.trend_rate = 0.5

        mock_dexcom = MagicMock()
        mock_dexcom.get_glucose_readings.return_value = [mock_reading]
        mock_dexcom_class.return_value = mock_dexcom

        email = unique_email("sync_mock")
        password = "SecurePass123"

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            # Register and login
            await client.post(
                "/api/auth/register",
                json={"email": email, "password": password},
            )

            login_response = await client.post(
                "/api/auth/login",
                json={"email": email, "password": password},
            )

            session_cookie = login_response.cookies.get(settings.jwt_cookie_name)

            # Connect Dexcom first
            await client.post(
                "/api/integrations/dexcom",
                json={
                    "username": "dexcom@example.com",
                    "password": "dexcom_password",
                },
                cookies={settings.jwt_cookie_name: session_cookie},
            )

            # Now sync
            response = await client.post(
                "/api/integrations/dexcom/sync",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200
        data = response.json()
        assert data["message"] == "Sync completed successfully"
        assert data["readings_fetched"] == 1
        assert data["readings_stored"] == 1

    async def test_get_glucose_history_empty(self):
        """Test glucose history returns empty when no readings."""
        email = unique_email("history_empty")
        password = "SecurePass123"

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            # Register and login
            await client.post(
                "/api/auth/register",
                json={"email": email, "password": password},
            )

            login_response = await client.post(
                "/api/auth/login",
                json={"email": email, "password": password},
            )

            session_cookie = login_response.cookies.get(settings.jwt_cookie_name)

            # Get history (should be empty)
            response = await client.get(
                "/api/integrations/glucose/history",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200
        data = response.json()
        assert data["readings"] == []
        assert data["count"] == 0

    async def test_get_glucose_history_with_params(self):
        """Test glucose history accepts query parameters."""
        email = unique_email("history_params")
        password = "SecurePass123"

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            # Register and login
            await client.post(
                "/api/auth/register",
                json={"email": email, "password": password},
            )

            login_response = await client.post(
                "/api/auth/login",
                json={"email": email, "password": password},
            )

            session_cookie = login_response.cookies.get(settings.jwt_cookie_name)

            # Get history with parameters
            response = await client.get(
                "/api/integrations/glucose/history?minutes=60&limit=12",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200
