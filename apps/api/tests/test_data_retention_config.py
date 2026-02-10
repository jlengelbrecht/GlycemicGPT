"""Story 9.3: Tests for data retention configuration service and settings router."""

import uuid
from unittest.mock import AsyncMock, MagicMock

import pytest
from pydantic import ValidationError

from src.config import settings
from src.models.data_retention_config import (
    DEFAULT_ANALYSIS_RETENTION_DAYS,
    DEFAULT_AUDIT_RETENTION_DAYS,
    DEFAULT_GLUCOSE_RETENTION_DAYS,
    DataRetentionConfig,
)
from src.schemas.data_retention_config import (
    DataRetentionConfigDefaults,
    DataRetentionConfigUpdate,
)
from src.services.data_retention_config import (
    enforce_retention_for_user,
    get_or_create_config,
    update_config,
)


def unique_email(prefix: str = "test") -> str:
    """Generate a unique email for testing."""
    return f"{prefix}_{uuid.uuid4().hex[:8]}@example.com"


async def register_and_login(client) -> str:
    """Register a new user and return the session cookie value."""
    email = unique_email("data_retention")
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


class TestDataRetentionConfigUpdate:
    """Tests for DataRetentionConfigUpdate schema validation."""

    def test_all_none_is_valid(self):
        update = DataRetentionConfigUpdate()
        assert update.glucose_retention_days is None
        assert update.analysis_retention_days is None
        assert update.audit_retention_days is None

    def test_partial_update_glucose(self):
        update = DataRetentionConfigUpdate(glucose_retention_days=90)
        assert update.glucose_retention_days == 90
        assert update.analysis_retention_days is None

    def test_partial_update_analysis(self):
        update = DataRetentionConfigUpdate(analysis_retention_days=180)
        assert update.analysis_retention_days == 180

    def test_partial_update_audit(self):
        update = DataRetentionConfigUpdate(audit_retention_days=365)
        assert update.audit_retention_days == 365

    def test_below_minimum_fails(self):
        with pytest.raises(ValidationError):
            DataRetentionConfigUpdate(glucose_retention_days=29)

    def test_above_maximum_fails(self):
        with pytest.raises(ValidationError):
            DataRetentionConfigUpdate(glucose_retention_days=3651)

    def test_boundary_minimum_passes(self):
        update = DataRetentionConfigUpdate(glucose_retention_days=30)
        assert update.glucose_retention_days == 30

    def test_boundary_maximum_passes(self):
        update = DataRetentionConfigUpdate(glucose_retention_days=3650)
        assert update.glucose_retention_days == 3650

    def test_all_fields_valid(self):
        update = DataRetentionConfigUpdate(
            glucose_retention_days=90,
            analysis_retention_days=180,
            audit_retention_days=365,
        )
        assert update.glucose_retention_days == 90
        assert update.analysis_retention_days == 180
        assert update.audit_retention_days == 365


class TestDataRetentionConfigDefaults:
    """Tests for DataRetentionConfigDefaults schema."""

    def test_default_values(self):
        defaults = DataRetentionConfigDefaults()
        assert defaults.glucose_retention_days == DEFAULT_GLUCOSE_RETENTION_DAYS
        assert defaults.analysis_retention_days == DEFAULT_ANALYSIS_RETENTION_DAYS
        assert defaults.audit_retention_days == DEFAULT_AUDIT_RETENTION_DAYS


# ── Service tests ──


class TestGetOrCreateConfig:
    """Tests for get_or_create_config service function."""

    @pytest.mark.asyncio
    async def test_creates_defaults_when_none_exist(self):
        """Should create a new DataRetentionConfig with defaults."""
        user_id = uuid.uuid4()

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = None

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result
        mock_db.commit = AsyncMock()
        mock_db.refresh = AsyncMock(return_value=None)

        result = await get_or_create_config(user_id, mock_db)

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

        result = await get_or_create_config(user_id, mock_db)

        assert result == existing
        mock_db.add.assert_not_called()
        mock_db.commit.assert_not_called()


class TestUpdateConfig:
    """Tests for update_config service function."""

    @pytest.mark.asyncio
    async def test_partial_update_glucose(self):
        """Should only update glucose_retention_days."""
        user_id = uuid.uuid4()
        existing = MagicMock()
        existing.user_id = user_id
        existing.glucose_retention_days = DEFAULT_GLUCOSE_RETENTION_DAYS
        existing.analysis_retention_days = DEFAULT_ANALYSIS_RETENTION_DAYS
        existing.audit_retention_days = DEFAULT_AUDIT_RETENTION_DAYS

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        updates = DataRetentionConfigUpdate(glucose_retention_days=90)
        result = await update_config(user_id, updates, mock_db)

        assert result.glucose_retention_days == 90
        mock_db.commit.assert_called_once()

    @pytest.mark.asyncio
    async def test_empty_update_no_change(self):
        """Empty update should still commit without errors."""
        user_id = uuid.uuid4()
        existing = MagicMock()
        existing.user_id = user_id

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        updates = DataRetentionConfigUpdate()
        result = await update_config(user_id, updates, mock_db)

        assert result == existing
        mock_db.commit.assert_called_once()


# ── Enforcement tests ──


class TestEnforceRetention:
    """Tests for enforce_retention_for_user service function."""

    @pytest.mark.asyncio
    async def test_deletes_expired_records(self):
        """Should execute delete queries for all categories."""
        user_id = uuid.uuid4()
        config = MagicMock(spec=DataRetentionConfig)
        config.glucose_retention_days = 30
        config.analysis_retention_days = 30
        config.audit_retention_days = 30

        mock_result = MagicMock()
        mock_result.rowcount = 5

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        result = await enforce_retention_for_user(user_id, config, mock_db)

        # 9 delete queries (glucose, pump, daily_brief, meal, correction,
        # suggestion, safety, alert, escalation)
        assert mock_db.execute.call_count == 9
        mock_db.commit.assert_called_once()

        # Each category returned 5 deleted
        assert result["glucose_readings"] == 5
        assert result["pump_events"] == 5
        assert result["daily_briefs"] == 5
        assert result["alerts"] == 5

    @pytest.mark.asyncio
    async def test_returns_zero_when_nothing_expired(self):
        """Should return zero counts when no records match."""
        user_id = uuid.uuid4()
        config = MagicMock(spec=DataRetentionConfig)
        config.glucose_retention_days = 3650
        config.analysis_retention_days = 3650
        config.audit_retention_days = 3650

        mock_result = MagicMock()
        mock_result.rowcount = 0

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        result = await enforce_retention_for_user(user_id, config, mock_db)

        assert all(v == 0 for v in result.values())


# ── Endpoint tests ──


class TestGetDataRetentionEndpoint:
    """Tests for GET /api/settings/data-retention."""

    @pytest.mark.asyncio
    async def test_unauthenticated_returns_401(self, client):
        response = await client.get("/api/settings/data-retention")
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_authenticated_returns_defaults(self, client):
        cookie = await register_and_login(client)
        response = await client.get(
            "/api/settings/data-retention",
            cookies={settings.jwt_cookie_name: cookie},
        )
        assert response.status_code == 200
        data = response.json()
        assert data["glucose_retention_days"] == DEFAULT_GLUCOSE_RETENTION_DAYS
        assert data["analysis_retention_days"] == DEFAULT_ANALYSIS_RETENTION_DAYS
        assert data["audit_retention_days"] == DEFAULT_AUDIT_RETENTION_DAYS
        assert "id" in data
        assert "updated_at" in data

    @pytest.mark.asyncio
    async def test_idempotent_get_or_create(self, client):
        """Calling GET twice should return the same record."""
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        r1 = await client.get("/api/settings/data-retention", cookies=cookies)
        r2 = await client.get("/api/settings/data-retention", cookies=cookies)

        assert r1.json()["id"] == r2.json()["id"]


class TestPatchDataRetentionEndpoint:
    """Tests for PATCH /api/settings/data-retention."""

    @pytest.mark.asyncio
    async def test_unauthenticated_returns_401(self, client):
        response = await client.patch(
            "/api/settings/data-retention",
            json={"glucose_retention_days": 90},
        )
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_invalid_range_returns_422(self, client):
        cookie = await register_and_login(client)
        response = await client.patch(
            "/api/settings/data-retention",
            json={"glucose_retention_days": 10},
            cookies={settings.jwt_cookie_name: cookie},
        )
        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_valid_partial_update(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.patch(
            "/api/settings/data-retention",
            json={"glucose_retention_days": 90},
            cookies=cookies,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["glucose_retention_days"] == 90
        assert data["analysis_retention_days"] == DEFAULT_ANALYSIS_RETENTION_DAYS

    @pytest.mark.asyncio
    async def test_update_all_fields(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.patch(
            "/api/settings/data-retention",
            json={
                "glucose_retention_days": 90,
                "analysis_retention_days": 180,
                "audit_retention_days": 365,
            },
            cookies=cookies,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["glucose_retention_days"] == 90
        assert data["analysis_retention_days"] == 180
        assert data["audit_retention_days"] == 365

    @pytest.mark.asyncio
    async def test_persists_across_requests(self, client):
        """Updated values should persist when fetched again."""
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        await client.patch(
            "/api/settings/data-retention",
            json={"audit_retention_days": 365},
            cookies=cookies,
        )

        response = await client.get(
            "/api/settings/data-retention",
            cookies=cookies,
        )
        assert response.json()["audit_retention_days"] == 365

    @pytest.mark.asyncio
    async def test_empty_body_returns_200(self, client):
        """Empty PATCH body should succeed without modifying anything."""
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.patch(
            "/api/settings/data-retention",
            json={},
            cookies=cookies,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["glucose_retention_days"] == DEFAULT_GLUCOSE_RETENTION_DAYS


class TestGetDataRetentionDefaultsEndpoint:
    """Tests for GET /api/settings/data-retention/defaults."""

    @pytest.mark.asyncio
    async def test_returns_defaults_without_auth(self, client):
        response = await client.get("/api/settings/data-retention/defaults")
        assert response.status_code == 200
        data = response.json()
        assert data["glucose_retention_days"] == DEFAULT_GLUCOSE_RETENTION_DAYS
        assert data["analysis_retention_days"] == DEFAULT_ANALYSIS_RETENTION_DAYS
        assert data["audit_retention_days"] == DEFAULT_AUDIT_RETENTION_DAYS


class TestGetStorageUsageEndpoint:
    """Tests for GET /api/settings/data-retention/usage."""

    @pytest.mark.asyncio
    async def test_unauthenticated_returns_401(self, client):
        response = await client.get("/api/settings/data-retention/usage")
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_authenticated_returns_usage(self, client):
        cookie = await register_and_login(client)
        response = await client.get(
            "/api/settings/data-retention/usage",
            cookies={settings.jwt_cookie_name: cookie},
        )
        assert response.status_code == 200
        data = response.json()
        assert "glucose_records" in data
        assert "pump_records" in data
        assert "analysis_records" in data
        assert "audit_records" in data
        assert "total_records" in data
        # Fresh user should have zero records
        assert data["total_records"] == 0
