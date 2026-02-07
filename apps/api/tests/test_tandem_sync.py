"""Story 3.4 & 3.5: Tests for Tandem pump data ingestion and Control-IQ parsing."""

import uuid
from datetime import UTC, datetime
from unittest.mock import MagicMock, patch

from httpx import ASGITransport, AsyncClient

from src.config import settings
from src.main import app
from src.models.pump_data import ControlIQMode, PumpEventType
from src.services.tandem_sync import (
    calculate_basal_adjustment,
    detect_control_iq_mode,
    map_event_type,
    parse_control_iq_event,
)


def unique_email(prefix: str = "test") -> str:
    """Generate a unique email for testing."""
    return f"{prefix}_{uuid.uuid4().hex[:8]}@example.com"


class TestEventTypeMapping:
    """Tests for Tandem event type mapping."""

    def test_map_basal_event(self):
        """Test mapping basal event."""
        event_type, is_automated, reason = map_event_type({"type": "basal"})
        assert event_type == PumpEventType.BASAL
        assert is_automated is False
        assert reason is None

    def test_map_automated_basal_event(self):
        """Test mapping automated basal event."""
        event_type, is_automated, reason = map_event_type(
            {
                "type": "autoBasal",
                "isAutomated": True,
            }
        )
        assert event_type == PumpEventType.BASAL
        assert is_automated is True
        assert reason == "basal_adjustment"

    def test_map_bolus_event(self):
        """Test mapping bolus event."""
        event_type, is_automated, reason = map_event_type({"type": "bolus"})
        assert event_type == PumpEventType.BOLUS
        assert is_automated is False
        assert reason is None

    def test_map_correction_bolus_event(self):
        """Test mapping correction bolus event (Control-IQ)."""
        event_type, is_automated, reason = map_event_type(
            {
                "type": "correctionBolus",
            }
        )
        assert event_type == PumpEventType.CORRECTION
        assert is_automated is True
        assert reason == "correction"

    def test_map_automated_correction_event(self):
        """Test mapping automated correction event."""
        event_type, is_automated, reason = map_event_type(
            {
                "type": "correction",
                "isAutomated": True,
            }
        )
        assert event_type == PumpEventType.CORRECTION
        assert is_automated is True
        assert reason == "correction"

    def test_map_suspend_event(self):
        """Test mapping suspend event."""
        event_type, is_automated, reason = map_event_type({"type": "suspend"})
        assert event_type == PumpEventType.SUSPEND
        assert is_automated is False

    def test_map_automated_suspend_event(self):
        """Test mapping automated suspend event (Control-IQ)."""
        event_type, is_automated, reason = map_event_type(
            {
                "type": "autoSuspend",
                "isAutomated": True,
            }
        )
        assert event_type == PumpEventType.SUSPEND
        assert is_automated is True
        assert reason == "suspend"

    def test_map_resume_event(self):
        """Test mapping resume event."""
        event_type, is_automated, reason = map_event_type({"type": "resume"})
        assert event_type == PumpEventType.RESUME
        assert is_automated is False

    def test_map_unknown_event_defaults_to_bolus(self):
        """Test that unknown event types default to bolus."""
        event_type, is_automated, reason = map_event_type({"type": "unknown"})
        assert event_type == PumpEventType.BOLUS
        assert is_automated is False

    # Issue #10: Missing malformed data tests
    def test_map_event_type_handles_missing_type(self):
        """Test handling of events with missing type field."""
        event_type, is_automated, reason = map_event_type({})
        assert event_type == PumpEventType.BOLUS  # Default
        assert is_automated is False

    def test_map_event_type_handles_none_type(self):
        """Test handling of None type value."""
        event_type, is_automated, reason = map_event_type({"type": None})
        assert event_type == PumpEventType.BOLUS


class TestTandemSyncEndpoints:
    """Tests for Tandem sync endpoints."""

    async def test_tandem_sync_requires_auth(self):
        """Test that Tandem sync endpoint requires authentication."""
        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            response = await client.post("/api/integrations/tandem/sync")

        assert response.status_code == 401

    async def test_tandem_sync_status_requires_auth(self):
        """Test that Tandem sync status endpoint requires authentication."""
        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            response = await client.get("/api/integrations/tandem/sync/status")

        assert response.status_code == 401

    async def test_tandem_sync_not_configured(self):
        """Test sync fails when Tandem is not configured."""
        email = unique_email("tandem_sync_nocred")
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
                "/api/integrations/tandem/sync",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 404
        assert "not configured" in response.json()["detail"].lower()

    async def test_get_tandem_sync_status_not_configured(self):
        """Test sync status when Tandem is not configured."""
        email = unique_email("tandem_status")
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
                "/api/integrations/tandem/sync/status",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200
        data = response.json()
        assert data["integration_status"] == "not_configured"
        assert data["events_available"] == 0

    @patch("src.services.tandem_sync.TandemSourceApi")
    @patch("src.routers.integrations.validate_tandem_credentials")
    async def test_sync_tandem_with_mocked_data(self, mock_validate, mock_tandem_class):
        """Test Tandem sync with mocked Tandem API."""
        mock_validate.return_value = (True, None)

        # Create mock events response
        mock_events = [
            {
                "type": "bolus",
                "timestamp": datetime.now(UTC).isoformat(),
                "units": 2.5,
                "iob": 3.2,
            },
            {
                "type": "autoBasal",
                "timestamp": datetime.now(UTC).isoformat(),
                "units": 0.8,
                "isAutomated": True,
                "duration": 30,
            },
        ]

        mock_api = MagicMock()
        mock_api.get_events.return_value = mock_events
        mock_tandem_class.return_value = mock_api

        email = unique_email("tandem_sync_mock")
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

            # Connect Tandem first
            await client.post(
                "/api/integrations/tandem",
                json={
                    "username": "tandem@example.com",
                    "password": "tandem_password",
                },
                cookies={settings.jwt_cookie_name: session_cookie},
            )

            # Now sync
            response = await client.post(
                "/api/integrations/tandem/sync",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200
        data = response.json()
        assert data["message"] == "Sync completed successfully"
        assert data["events_fetched"] == 2
        assert data["events_stored"] == 2

    @patch("src.services.tandem_sync.TandemSourceApi")
    @patch("src.routers.integrations.validate_tandem_credentials")
    async def test_sync_tandem_control_iq_flagging(
        self, mock_validate, mock_tandem_class
    ):
        """Test that Control-IQ events are properly flagged as automated."""
        mock_validate.return_value = (True, None)

        # Create mock Control-IQ correction event
        mock_events = [
            {
                "type": "correctionBolus",
                "timestamp": datetime.now(UTC).isoformat(),
                "units": 0.5,
                "isAutomated": True,
                "iob": 2.1,
                "bg": 180,
            },
        ]

        mock_api = MagicMock()
        mock_api.get_events.return_value = mock_events
        mock_tandem_class.return_value = mock_api

        email = unique_email("tandem_controliq")
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

            # Connect Tandem first
            await client.post(
                "/api/integrations/tandem",
                json={
                    "username": "tandem@example.com",
                    "password": "tandem_password",
                },
                cookies={settings.jwt_cookie_name: session_cookie},
            )

            # Sync
            response = await client.post(
                "/api/integrations/tandem/sync",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200
        data = response.json()
        assert data["events_stored"] == 1
        # Verify the last event is automated
        assert data["last_event"]["is_automated"] is True
        assert data["last_event"]["event_type"] == "correction"

    @patch("src.services.tandem_sync.TandemSourceApi")
    @patch("src.routers.integrations.validate_tandem_credentials")
    async def test_sync_tandem_empty_response(self, mock_validate, mock_tandem_class):
        """Test Tandem sync with no events."""
        mock_validate.return_value = (True, None)

        mock_api = MagicMock()
        mock_api.get_events.return_value = []
        mock_tandem_class.return_value = mock_api

        email = unique_email("tandem_empty")
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

            # Connect Tandem first
            await client.post(
                "/api/integrations/tandem",
                json={
                    "username": "tandem@example.com",
                    "password": "tandem_password",
                },
                cookies={settings.jwt_cookie_name: session_cookie},
            )

            # Sync
            response = await client.post(
                "/api/integrations/tandem/sync",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200
        data = response.json()
        assert data["events_fetched"] == 0
        assert data["events_stored"] == 0
        assert data["last_event"] is None

    @patch("src.services.tandem_sync.TandemSourceApi")
    @patch("src.routers.integrations.validate_tandem_credentials")
    async def test_tandem_sync_status_after_sync(
        self, mock_validate, mock_tandem_class
    ):
        """Test sync status after a successful sync."""
        mock_validate.return_value = (True, None)

        mock_events = [
            {
                "type": "bolus",
                "timestamp": datetime.now(UTC).isoformat(),
                "units": 3.0,
            },
        ]

        mock_api = MagicMock()
        mock_api.get_events.return_value = mock_events
        mock_tandem_class.return_value = mock_api

        email = unique_email("tandem_status_sync")
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

            # Connect and sync
            await client.post(
                "/api/integrations/tandem",
                json={
                    "username": "tandem@example.com",
                    "password": "tandem_password",
                },
                cookies={settings.jwt_cookie_name: session_cookie},
            )

            await client.post(
                "/api/integrations/tandem/sync",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

            # Check status
            response = await client.get(
                "/api/integrations/tandem/sync/status",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200
        data = response.json()
        assert data["integration_status"] == "connected"
        assert data["events_available"] == 1
        assert data["last_sync_at"] is not None
        assert data["latest_event"] is not None

    # Issue #10: Test for skipping events without timestamp
    @patch("src.services.tandem_sync.TandemSourceApi")
    @patch("src.routers.integrations.validate_tandem_credentials")
    async def test_sync_skips_events_without_timestamp(
        self, mock_validate, mock_tandem_class
    ):
        """Test that events without valid timestamps are skipped."""
        mock_validate.return_value = (True, None)

        mock_events = [
            {"type": "bolus", "units": 2.5},  # No timestamp - should skip
            {
                "type": "bolus",
                "timestamp": "invalid-date",
                "units": 1.0,
            },  # Invalid - skip
            {
                "type": "bolus",
                "timestamp": datetime.now(UTC).isoformat(),
                "units": 3.0,
            },  # Valid
        ]

        mock_api = MagicMock()
        mock_api.get_events.return_value = mock_events
        mock_tandem_class.return_value = mock_api

        email = unique_email("tandem_skip_invalid")
        password = "SecurePass123"

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            await client.post(
                "/api/auth/register",
                json={"email": email, "password": password},
            )

            login_response = await client.post(
                "/api/auth/login",
                json={"email": email, "password": password},
            )

            session_cookie = login_response.cookies.get(settings.jwt_cookie_name)

            await client.post(
                "/api/integrations/tandem",
                json={
                    "username": "tandem@example.com",
                    "password": "tandem_password",
                },
                cookies={settings.jwt_cookie_name: session_cookie},
            )

            response = await client.post(
                "/api/integrations/tandem/sync",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200
        data = response.json()
        # Should only store 1 event (the valid one)
        assert data["events_stored"] == 1

    # Issue #1: Test region configuration
    @patch("src.services.tandem_sync.TandemSourceApi")
    @patch("src.routers.integrations.validate_tandem_credentials")
    async def test_tandem_connect_with_eu_region(
        self, mock_validate, mock_tandem_class
    ):
        """Test connecting Tandem with EU region."""
        mock_validate.return_value = (True, None)
        mock_api = MagicMock()
        mock_tandem_class.return_value = mock_api

        email = unique_email("tandem_eu_region")
        password = "SecurePass123"

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            await client.post(
                "/api/auth/register",
                json={"email": email, "password": password},
            )

            login_response = await client.post(
                "/api/auth/login",
                json={"email": email, "password": password},
            )

            session_cookie = login_response.cookies.get(settings.jwt_cookie_name)

            # Connect with EU region
            response = await client.post(
                "/api/integrations/tandem",
                json={
                    "username": "tandem@example.com",
                    "password": "tandem_password",
                    "region": "EU",
                },
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 201
        # Verify validate was called with EU region
        mock_validate.assert_called_with(
            "tandem@example.com",
            "tandem_password",
            "EU",
        )


# Issue #4: Tests for get_pump_events helper function
# Note: The get_pump_events function is tested indirectly through:
# 1. TestTandemSyncService tests (sync stores events, which get_pump_events retrieves)
# 2. test_get_tandem_sync_status_* tests (sync status endpoint uses get_pump_events)


# Story 3.5: Tests for Control-IQ Activity Parsing


class TestControlIQModeDetection:
    """Tests for Control-IQ mode detection (Story 3.5)."""

    def test_detect_sleep_mode_from_activity_type(self):
        """Test detecting Sleep mode from activityType field."""
        mode = detect_control_iq_mode({"activityType": "Sleep"})
        assert mode == ControlIQMode.SLEEP

    def test_detect_exercise_mode_from_activity_type(self):
        """Test detecting Exercise mode from activityType field."""
        mode = detect_control_iq_mode({"activityType": "Exercise"})
        assert mode == ControlIQMode.EXERCISE

    def test_detect_standard_mode(self):
        """Test detecting Standard mode."""
        mode = detect_control_iq_mode({"mode": "standard"})
        assert mode == ControlIQMode.STANDARD

    def test_detect_sleep_mode_from_flag(self):
        """Test detecting Sleep mode from boolean flag."""
        mode = detect_control_iq_mode({"isSleepMode": True})
        assert mode == ControlIQMode.SLEEP

    def test_detect_exercise_mode_from_flag(self):
        """Test detecting Exercise mode from boolean flag."""
        mode = detect_control_iq_mode({"isExerciseMode": True})
        assert mode == ControlIQMode.EXERCISE

    def test_detect_mode_returns_none_for_unknown(self):
        """Test that unknown mode returns None."""
        mode = detect_control_iq_mode({"type": "bolus"})
        assert mode is None

    def test_detect_mode_case_insensitive(self):
        """Test that mode detection is case insensitive."""
        mode = detect_control_iq_mode({"activityType": "SLEEP"})
        assert mode == ControlIQMode.SLEEP


class TestBasalAdjustmentCalculation:
    """Tests for basal adjustment percentage calculation (Story 3.5)."""

    def test_calculate_adjustment_from_direct_percentage(self):
        """Test getting adjustment from direct percentage field."""
        adj = calculate_basal_adjustment({"adjustmentPercent": 25.0})
        assert adj == 25.0

    def test_calculate_adjustment_from_rates(self):
        """Test calculating adjustment from profile vs actual rates."""
        # Profile rate: 1.0, Actual rate: 1.5 = 50% increase
        adj = calculate_basal_adjustment(
            {
                "profileRate": 1.0,
                "rate": 1.5,
            }
        )
        assert adj == 50.0

    def test_calculate_adjustment_decrease(self):
        """Test calculating a decrease in basal rate."""
        # Profile rate: 1.0, Actual rate: 0.5 = 50% decrease
        adj = calculate_basal_adjustment(
            {
                "profileRate": 1.0,
                "rate": 0.5,
            }
        )
        assert adj == -50.0

    def test_calculate_adjustment_returns_none_when_missing_data(self):
        """Test that None is returned when data is missing."""
        adj = calculate_basal_adjustment({"type": "basal"})
        assert adj is None

    def test_calculate_adjustment_with_zero_profile_rate(self):
        """Test handling of zero profile rate (avoid division by zero)."""
        adj = calculate_basal_adjustment(
            {
                "profileRate": 0,
                "rate": 1.0,
            }
        )
        assert adj is None


class TestParseControlIQEvent:
    """Tests for full Control-IQ event parsing (Story 3.5)."""

    def test_parse_automated_correction(self):
        """Test parsing an automated correction bolus."""
        parsed = parse_control_iq_event(
            {
                "type": "correction",
                "isAutomated": True,
                "units": 0.5,
            }
        )
        assert parsed.event_type == PumpEventType.CORRECTION
        assert parsed.is_automated is True
        assert parsed.control_iq_reason == "correction"

    def test_parse_basal_increase_with_mode(self):
        """Test parsing a basal increase in Sleep mode."""
        parsed = parse_control_iq_event(
            {
                "type": "basal",
                "isAutomated": True,
                "profileRate": 1.0,
                "rate": 1.5,
                "activityType": "Sleep",
            }
        )
        assert parsed.event_type == PumpEventType.BASAL
        assert parsed.is_automated is True
        assert parsed.control_iq_reason == "basal_increase"
        assert parsed.control_iq_mode == ControlIQMode.SLEEP
        assert parsed.basal_adjustment_pct == 50.0

    def test_parse_basal_decrease(self):
        """Test parsing a basal decrease."""
        parsed = parse_control_iq_event(
            {
                "type": "basal",
                "isAutomated": True,
                "profileRate": 1.0,
                "rate": 0.3,
            }
        )
        assert parsed.event_type == PumpEventType.BASAL
        assert parsed.control_iq_reason == "basal_decrease"
        assert parsed.basal_adjustment_pct == -70.0

    def test_parse_manual_bolus_no_mode(self):
        """Test parsing a manual bolus has no Control-IQ mode."""
        parsed = parse_control_iq_event(
            {
                "type": "bolus",
                "units": 5.0,
            }
        )
        assert parsed.event_type == PumpEventType.BOLUS
        assert parsed.is_automated is False
        assert parsed.control_iq_mode is None
        assert parsed.basal_adjustment_pct is None

    def test_parse_automated_suspend(self):
        """Test parsing an automated suspend (predicted low)."""
        parsed = parse_control_iq_event(
            {
                "type": "autoSuspend",
                "isAutomated": True,
            }
        )
        assert parsed.event_type == PumpEventType.SUSPEND
        assert parsed.is_automated is True
        assert parsed.control_iq_reason == "suspend"


class TestControlIQActivityEndpoint:
    """Tests for the Control-IQ activity endpoint (Story 3.5)."""

    async def test_control_iq_activity_requires_auth(self):
        """Test that the endpoint requires authentication."""
        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            response = await client.get("/api/integrations/tandem/control-iq/activity")
        assert response.status_code == 401

    async def test_control_iq_activity_empty_result(self):
        """Test activity endpoint returns zeros when no events exist."""
        email = unique_email("controliq_empty")
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
            login_resp = await client.post(
                "/api/auth/login",
                json={"email": email, "password": password},
            )
            session_cookie = login_resp.cookies.get(settings.jwt_cookie_name)

            response = await client.get(
                "/api/integrations/tandem/control-iq/activity",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200
        data = response.json()
        assert data["total_events"] == 0
        assert data["automated_events"] == 0
        assert data["correction_count"] == 0
        assert data["hours_analyzed"] == 24

    async def test_control_iq_activity_custom_hours(self):
        """Test activity endpoint accepts custom hours parameter."""
        email = unique_email("controliq_hours")
        password = "SecurePass123"

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            await client.post(
                "/api/auth/register",
                json={"email": email, "password": password},
            )
            login_resp = await client.post(
                "/api/auth/login",
                json={"email": email, "password": password},
            )
            session_cookie = login_resp.cookies.get(settings.jwt_cookie_name)

            response = await client.get(
                "/api/integrations/tandem/control-iq/activity",
                params={"hours": 48},
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200
        data = response.json()
        assert data["hours_analyzed"] == 48
