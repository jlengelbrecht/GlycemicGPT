"""Story 9.1: Tests for target glucose range service and settings router."""

import uuid
from unittest.mock import AsyncMock, MagicMock

import pytest
from pydantic import ValidationError

from src.config import settings
from src.schemas.target_glucose_range import (
    TargetGlucoseRangeDefaults,
    TargetGlucoseRangeUpdate,
)
from src.services.target_glucose_range import get_or_create_range, update_range


def unique_email(prefix: str = "test") -> str:
    """Generate a unique email for testing."""
    return f"{prefix}_{uuid.uuid4().hex[:8]}@example.com"


async def register_and_login(client) -> str:
    """Register a new user and return the session cookie value."""
    email = unique_email("glucose_range")
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


class TestTargetGlucoseRangeUpdate:
    """Tests for TargetGlucoseRangeUpdate schema validation."""

    def test_all_none_is_valid(self):
        update = TargetGlucoseRangeUpdate()
        assert update.low_target is None
        assert update.high_target is None

    def test_partial_update_low(self):
        update = TargetGlucoseRangeUpdate(low_target=80.0)
        assert update.low_target == 80.0
        assert update.high_target is None

    def test_partial_update_high(self):
        update = TargetGlucoseRangeUpdate(high_target=200.0)
        assert update.high_target == 200.0
        assert update.low_target is None

    def test_low_ge_high_fails(self):
        with pytest.raises(
            ValidationError, match="low_target must be less than high_target"
        ):
            # Both values within field ranges (40-200, 80-400) but low >= high
            TargetGlucoseRangeUpdate(low_target=150.0, high_target=100.0)

    def test_low_equal_high_fails(self):
        with pytest.raises(
            ValidationError, match="low_target must be less than high_target"
        ):
            TargetGlucoseRangeUpdate(low_target=100.0, high_target=100.0)

    def test_valid_ordering_passes(self):
        update = TargetGlucoseRangeUpdate(low_target=65.0, high_target=200.0)
        assert update.low_target == 65.0
        assert update.high_target == 200.0

    def test_low_below_range_fails(self):
        with pytest.raises(ValidationError):
            TargetGlucoseRangeUpdate(low_target=30.0)

    def test_low_above_range_fails(self):
        with pytest.raises(ValidationError):
            TargetGlucoseRangeUpdate(low_target=210.0)

    def test_high_below_range_fails(self):
        with pytest.raises(ValidationError):
            TargetGlucoseRangeUpdate(high_target=70.0)

    def test_high_above_range_fails(self):
        with pytest.raises(ValidationError):
            TargetGlucoseRangeUpdate(high_target=410.0)


class TestTargetGlucoseRangeDefaults:
    """Tests for TargetGlucoseRangeDefaults schema."""

    def test_default_values(self):
        defaults = TargetGlucoseRangeDefaults()
        assert defaults.low_target == 70.0
        assert defaults.high_target == 180.0


# ── Service tests ──


class TestGetOrCreateRange:
    """Tests for get_or_create_range service function."""

    @pytest.mark.asyncio
    async def test_creates_defaults_when_none_exist(self):
        """Should create a new TargetGlucoseRange with defaults."""
        user_id = uuid.uuid4()

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = None

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result
        mock_db.commit = AsyncMock()
        mock_db.refresh = AsyncMock(return_value=None)

        result = await get_or_create_range(user_id, mock_db)

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

        result = await get_or_create_range(user_id, mock_db)

        assert result == existing
        mock_db.add.assert_not_called()
        mock_db.commit.assert_not_called()


class TestUpdateRange:
    """Tests for update_range service function."""

    @pytest.mark.asyncio
    async def test_partial_update_low_target(self):
        """Should only update low_target."""
        user_id = uuid.uuid4()
        existing = MagicMock()
        existing.user_id = user_id
        existing.low_target = 70.0
        existing.high_target = 180.0

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        updates = TargetGlucoseRangeUpdate(low_target=80.0)
        result = await update_range(user_id, updates, mock_db)

        assert result.low_target == 80.0
        mock_db.commit.assert_called_once()

    @pytest.mark.asyncio
    async def test_ordering_violation_low_ge_high(self):
        """Should raise ValueError when low_target >= high_target after merge."""
        user_id = uuid.uuid4()
        existing = MagicMock()
        existing.user_id = user_id
        existing.low_target = 70.0
        existing.high_target = 180.0

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        # Set low_target above existing high_target
        updates = TargetGlucoseRangeUpdate(low_target=190.0)

        with pytest.raises(
            ValueError, match="low_target.*must be less than.*high_target"
        ):
            await update_range(user_id, updates, mock_db)

    @pytest.mark.asyncio
    async def test_ordering_violation_equal(self):
        """Should raise ValueError when low_target == high_target after merge."""
        user_id = uuid.uuid4()
        existing = MagicMock()
        existing.user_id = user_id
        existing.low_target = 70.0
        existing.high_target = 180.0

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        updates = TargetGlucoseRangeUpdate(high_target=80.0)

        # After merge: low=70 >= high=80? No. Actually low=70 < high=80 so this passes.
        # Let's set low to same as new high instead
        updates = TargetGlucoseRangeUpdate(low_target=180.0)

        with pytest.raises(
            ValueError, match="low_target.*must be less than.*high_target"
        ):
            await update_range(user_id, updates, mock_db)


# ── Endpoint tests ──


class TestGetTargetGlucoseRangeEndpoint:
    """Tests for GET /api/settings/target-glucose-range."""

    @pytest.mark.asyncio
    async def test_unauthenticated_returns_401(self, client):
        response = await client.get("/api/settings/target-glucose-range")
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_authenticated_returns_defaults(self, client):
        cookie = await register_and_login(client)
        response = await client.get(
            "/api/settings/target-glucose-range",
            cookies={settings.jwt_cookie_name: cookie},
        )
        assert response.status_code == 200
        data = response.json()
        assert data["low_target"] == 70.0
        assert data["high_target"] == 180.0
        assert "id" in data
        assert "updated_at" in data

    @pytest.mark.asyncio
    async def test_idempotent_get_or_create(self, client):
        """Calling GET twice should return the same record."""
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        r1 = await client.get("/api/settings/target-glucose-range", cookies=cookies)
        r2 = await client.get("/api/settings/target-glucose-range", cookies=cookies)

        assert r1.json()["id"] == r2.json()["id"]


class TestPatchTargetGlucoseRangeEndpoint:
    """Tests for PATCH /api/settings/target-glucose-range."""

    @pytest.mark.asyncio
    async def test_unauthenticated_returns_401(self, client):
        response = await client.patch(
            "/api/settings/target-glucose-range",
            json={"low_target": 80.0},
        )
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_invalid_range_returns_422(self, client):
        cookie = await register_and_login(client)
        response = await client.patch(
            "/api/settings/target-glucose-range",
            json={"low_target": 30.0},
            cookies={settings.jwt_cookie_name: cookie},
        )
        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_ordering_violation_returns_422(self, client):
        """Setting low_target above existing high_target should fail."""
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        # Ensure range exists
        await client.get("/api/settings/target-glucose-range", cookies=cookies)

        response = await client.patch(
            "/api/settings/target-glucose-range",
            json={"low_target": 190.0},
            cookies=cookies,
        )
        assert response.status_code == 422
        assert "low_target" in response.json()["detail"]

    @pytest.mark.asyncio
    async def test_valid_partial_update(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.patch(
            "/api/settings/target-glucose-range",
            json={"low_target": 80.0},
            cookies=cookies,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["low_target"] == 80.0
        assert data["high_target"] == 180.0

    @pytest.mark.asyncio
    async def test_update_both_fields(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.patch(
            "/api/settings/target-glucose-range",
            json={"low_target": 65.0, "high_target": 200.0},
            cookies=cookies,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["low_target"] == 65.0
        assert data["high_target"] == 200.0

    @pytest.mark.asyncio
    async def test_persists_across_requests(self, client):
        """Updated values should persist when fetched again."""
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        await client.patch(
            "/api/settings/target-glucose-range",
            json={"high_target": 200.0},
            cookies=cookies,
        )

        response = await client.get(
            "/api/settings/target-glucose-range",
            cookies=cookies,
        )
        assert response.json()["high_target"] == 200.0

    @pytest.mark.asyncio
    async def test_empty_body_returns_200(self, client):
        """Empty PATCH body should succeed without modifying anything."""
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.patch(
            "/api/settings/target-glucose-range",
            json={},
            cookies=cookies,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["low_target"] == 70.0
        assert data["high_target"] == 180.0


class TestGetTargetGlucoseRangeDefaultsEndpoint:
    """Tests for GET /api/settings/target-glucose-range/defaults."""

    @pytest.mark.asyncio
    async def test_returns_defaults_without_auth(self, client):
        response = await client.get("/api/settings/target-glucose-range/defaults")
        assert response.status_code == 200
        data = response.json()
        assert data["low_target"] == 70.0
        assert data["high_target"] == 180.0
