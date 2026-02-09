"""Story 6.1: Tests for alert threshold service and settings router."""

import uuid
from unittest.mock import AsyncMock, MagicMock

import pytest
from httpx import AsyncClient
from pydantic import ValidationError

from src.config import settings
from src.schemas.alert_threshold import AlertThresholdDefaults, AlertThresholdUpdate
from src.services.alert_threshold import get_or_create_thresholds, update_thresholds


def unique_email(prefix: str = "test") -> str:
    """Generate a unique email for testing."""
    return f"{prefix}_{uuid.uuid4().hex[:8]}@example.com"


async def register_and_login(client: AsyncClient) -> str:
    """Register a new user and return the session cookie value."""
    email = unique_email("alerts")
    password = "SecurePass123"

    await client.post(
        "/api/auth/register",
        json={"email": email, "password": password},
    )

    login_response = await client.post(
        "/api/auth/login",
        json={"email": email, "password": password},
    )

    return login_response.cookies.get(settings.jwt_cookie_name)


# ── Schema validation tests ──


class TestAlertThresholdUpdate:
    """Tests for AlertThresholdUpdate schema validation."""

    def test_all_none_is_valid(self):
        update = AlertThresholdUpdate()
        assert update.low_warning is None
        assert update.urgent_low is None

    def test_partial_update(self):
        update = AlertThresholdUpdate(low_warning=80.0)
        assert update.low_warning == 80.0
        assert update.urgent_low is None

    def test_urgent_low_must_be_less_than_low_warning(self):
        with pytest.raises(
            ValidationError, match="urgent_low must be less than low_warning"
        ):
            AlertThresholdUpdate(urgent_low=75.0, low_warning=70.0)

    def test_urgent_low_equal_to_low_warning_fails(self):
        with pytest.raises(
            ValidationError, match="urgent_low must be less than low_warning"
        ):
            AlertThresholdUpdate(urgent_low=70.0, low_warning=70.0)

    def test_high_warning_must_be_less_than_urgent_high(self):
        with pytest.raises(
            ValidationError, match="high_warning must be less than urgent_high"
        ):
            AlertThresholdUpdate(high_warning=260.0, urgent_high=250.0)

    def test_valid_ordering_passes(self):
        update = AlertThresholdUpdate(
            urgent_low=50.0, low_warning=70.0, high_warning=180.0, urgent_high=250.0
        )
        assert update.urgent_low == 50.0
        assert update.urgent_high == 250.0

    def test_low_warning_below_range_fails(self):
        with pytest.raises(ValidationError):
            AlertThresholdUpdate(low_warning=30.0)

    def test_low_warning_above_range_fails(self):
        with pytest.raises(ValidationError):
            AlertThresholdUpdate(low_warning=110.0)

    def test_iob_warning_below_range_fails(self):
        with pytest.raises(ValidationError):
            AlertThresholdUpdate(iob_warning=0.1)

    def test_iob_warning_valid(self):
        update = AlertThresholdUpdate(iob_warning=5.0)
        assert update.iob_warning == 5.0


class TestAlertThresholdDefaults:
    """Tests for AlertThresholdDefaults schema."""

    def test_default_values(self):
        defaults = AlertThresholdDefaults()
        assert defaults.low_warning == 70.0
        assert defaults.urgent_low == 55.0
        assert defaults.high_warning == 180.0
        assert defaults.urgent_high == 250.0
        assert defaults.iob_warning == 3.0


# ── Service tests ──


class TestGetOrCreateThresholds:
    """Tests for get_or_create_thresholds service function."""

    @pytest.mark.asyncio
    async def test_creates_defaults_when_none_exist(self):
        """Should create a new AlertThreshold with defaults."""
        user_id = uuid.uuid4()

        # Mock DB returning None (no existing record)
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = None

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result
        mock_db.commit = AsyncMock()
        mock_db.refresh = AsyncMock(return_value=None)

        result = await get_or_create_thresholds(user_id, mock_db)

        # Verify it created a new record and persisted it
        assert result.user_id == user_id
        mock_db.add.assert_called_once_with(result)
        mock_db.commit.assert_called_once()
        mock_db.refresh.assert_called_once_with(result)

    @pytest.mark.asyncio
    async def test_returns_existing_when_found(self):
        """Should return existing record without creating."""
        user_id = uuid.uuid4()
        existing = MagicMock()
        existing.user_id = user_id

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        result = await get_or_create_thresholds(user_id, mock_db)

        assert result == existing
        mock_db.add.assert_not_called()
        mock_db.commit.assert_not_called()


class TestUpdateThresholds:
    """Tests for update_thresholds service function."""

    @pytest.mark.asyncio
    async def test_partial_update_applies_fields(self):
        """Should only update provided fields."""
        user_id = uuid.uuid4()
        existing = MagicMock()
        existing.user_id = user_id
        existing.low_warning = 70.0
        existing.urgent_low = 55.0
        existing.high_warning = 180.0
        existing.urgent_high = 250.0
        existing.iob_warning = 3.0

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        updates = AlertThresholdUpdate(low_warning=80.0)
        result = await update_thresholds(user_id, updates, mock_db)

        assert result.low_warning == 80.0
        mock_db.commit.assert_called_once()

    @pytest.mark.asyncio
    async def test_ordering_violation_urgent_low_ge_low_warning(self):
        """Should raise ValueError when urgent_low >= low_warning after merge."""
        user_id = uuid.uuid4()
        existing = MagicMock()
        existing.user_id = user_id
        existing.low_warning = 70.0
        existing.urgent_low = 55.0
        existing.high_warning = 180.0
        existing.urgent_high = 250.0
        existing.iob_warning = 3.0

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        # Set urgent_low higher than existing low_warning
        updates = AlertThresholdUpdate(urgent_low=75.0)

        with pytest.raises(
            ValueError, match="urgent_low.*must be less than.*low_warning"
        ):
            await update_thresholds(user_id, updates, mock_db)

    @pytest.mark.asyncio
    async def test_ordering_violation_high_warning_ge_urgent_high(self):
        """Should raise ValueError when high_warning >= urgent_high after merge."""
        user_id = uuid.uuid4()
        existing = MagicMock()
        existing.user_id = user_id
        existing.low_warning = 70.0
        existing.urgent_low = 55.0
        existing.high_warning = 180.0
        existing.urgent_high = 250.0
        existing.iob_warning = 3.0

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        updates = AlertThresholdUpdate(high_warning=260.0)

        with pytest.raises(
            ValueError, match="high_warning.*must be less than.*urgent_high"
        ):
            await update_thresholds(user_id, updates, mock_db)

    @pytest.mark.asyncio
    async def test_ordering_violation_low_warning_ge_high_warning(self):
        """Should raise ValueError when low_warning >= high_warning after merge."""
        user_id = uuid.uuid4()
        existing = MagicMock()
        existing.user_id = user_id
        existing.low_warning = 70.0
        existing.urgent_low = 55.0
        # Artificially low high_warning to trigger merge conflict
        existing.high_warning = 85.0
        existing.urgent_high = 250.0
        existing.iob_warning = 3.0

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        # Set low_warning=90 which exceeds existing high_warning=85
        updates = AlertThresholdUpdate(low_warning=90.0)

        with pytest.raises(
            ValueError, match="low_warning.*must be less than.*high_warning"
        ):
            await update_thresholds(user_id, updates, mock_db)

    @pytest.mark.asyncio
    async def test_iob_only_update_succeeds(self):
        """Should update iob_warning without affecting glucose threshold ordering."""
        user_id = uuid.uuid4()
        existing = MagicMock()
        existing.user_id = user_id
        existing.low_warning = 70.0
        existing.urgent_low = 55.0
        existing.high_warning = 180.0
        existing.urgent_high = 250.0
        existing.iob_warning = 3.0

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        updates = AlertThresholdUpdate(iob_warning=5.0)
        result = await update_thresholds(user_id, updates, mock_db)

        assert result.iob_warning == 5.0
        mock_db.commit.assert_called_once()


# ── Endpoint tests ──


class TestGetAlertThresholdsEndpoint:
    """Tests for GET /api/settings/alert-thresholds."""

    @pytest.mark.asyncio
    async def test_unauthenticated_returns_401(self, client):
        response = await client.get("/api/settings/alert-thresholds")
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_authenticated_returns_defaults(self, client):
        cookie = await register_and_login(client)
        response = await client.get(
            "/api/settings/alert-thresholds",
            cookies={settings.jwt_cookie_name: cookie},
        )
        assert response.status_code == 200
        data = response.json()
        assert data["low_warning"] == 70.0
        assert data["urgent_low"] == 55.0
        assert data["high_warning"] == 180.0
        assert data["urgent_high"] == 250.0
        assert data["iob_warning"] == 3.0
        assert "id" in data
        assert "updated_at" in data

    @pytest.mark.asyncio
    async def test_idempotent_get_or_create(self, client):
        """Calling GET twice should return the same record."""
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        r1 = await client.get("/api/settings/alert-thresholds", cookies=cookies)
        r2 = await client.get("/api/settings/alert-thresholds", cookies=cookies)

        assert r1.json()["id"] == r2.json()["id"]


class TestPatchAlertThresholdsEndpoint:
    """Tests for PATCH /api/settings/alert-thresholds."""

    @pytest.mark.asyncio
    async def test_unauthenticated_returns_401(self, client):
        response = await client.patch(
            "/api/settings/alert-thresholds",
            json={"low_warning": 80.0},
        )
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_invalid_range_returns_422(self, client):
        cookie = await register_and_login(client)
        response = await client.patch(
            "/api/settings/alert-thresholds",
            json={"low_warning": 10.0},
            cookies={settings.jwt_cookie_name: cookie},
        )
        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_ordering_violation_returns_422(self, client):
        """Setting urgent_low above existing low_warning should fail."""
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        # First, ensure thresholds exist
        await client.get("/api/settings/alert-thresholds", cookies=cookies)

        # Try to set urgent_low above the default low_warning (70)
        response = await client.patch(
            "/api/settings/alert-thresholds",
            json={"urgent_low": 75.0},
            cookies=cookies,
        )
        assert response.status_code == 422
        assert "urgent_low" in response.json()["detail"]

    @pytest.mark.asyncio
    async def test_valid_partial_update(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.patch(
            "/api/settings/alert-thresholds",
            json={"low_warning": 80.0},
            cookies=cookies,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["low_warning"] == 80.0
        # Unchanged values should remain at defaults
        assert data["urgent_low"] == 55.0
        assert data["high_warning"] == 180.0

    @pytest.mark.asyncio
    async def test_update_multiple_fields(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.patch(
            "/api/settings/alert-thresholds",
            json={
                "urgent_low": 50.0,
                "low_warning": 75.0,
                "high_warning": 200.0,
                "urgent_high": 300.0,
                "iob_warning": 4.5,
            },
            cookies=cookies,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["urgent_low"] == 50.0
        assert data["low_warning"] == 75.0
        assert data["high_warning"] == 200.0
        assert data["urgent_high"] == 300.0
        assert data["iob_warning"] == 4.5

    @pytest.mark.asyncio
    async def test_persists_across_requests(self, client):
        """Updated values should persist when fetched again."""
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        await client.patch(
            "/api/settings/alert-thresholds",
            json={"iob_warning": 6.0},
            cookies=cookies,
        )

        response = await client.get("/api/settings/alert-thresholds", cookies=cookies)
        assert response.json()["iob_warning"] == 6.0

    @pytest.mark.asyncio
    async def test_empty_body_returns_200(self, client):
        """Empty PATCH body should succeed without modifying anything."""
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.patch(
            "/api/settings/alert-thresholds",
            json={},
            cookies=cookies,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["low_warning"] == 70.0
        assert data["urgent_low"] == 55.0


class TestGetAlertThresholdDefaultsEndpoint:
    """Tests for GET /api/settings/alert-thresholds/defaults."""

    @pytest.mark.asyncio
    async def test_returns_defaults_without_auth(self, client):
        response = await client.get("/api/settings/alert-thresholds/defaults")
        assert response.status_code == 200
        data = response.json()
        assert data["low_warning"] == 70.0
        assert data["urgent_low"] == 55.0
        assert data["high_warning"] == 180.0
        assert data["urgent_high"] == 250.0
        assert data["iob_warning"] == 3.0
