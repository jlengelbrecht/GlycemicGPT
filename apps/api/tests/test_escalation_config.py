"""Story 6.6: Tests for escalation timing configuration."""

import uuid
from unittest.mock import AsyncMock, MagicMock

import pytest
from pydantic import ValidationError
from sqlalchemy.exc import IntegrityError

from src.config import settings
from src.schemas.escalation_config import (
    EscalationConfigDefaults,
    EscalationConfigUpdate,
)
from src.services.escalation_config import get_or_create_config, update_config


def unique_email(prefix: str = "test") -> str:
    """Generate a unique email for testing."""
    return f"{prefix}_{uuid.uuid4().hex[:8]}@example.com"


async def register_and_login(client) -> str:
    """Register a new user and return the session cookie value."""
    email = unique_email("esc")
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


class TestEscalationConfigUpdate:
    """Tests for EscalationConfigUpdate schema validation."""

    def test_all_none_is_valid(self):
        update = EscalationConfigUpdate()
        assert update.reminder_delay_minutes is None
        assert update.primary_contact_delay_minutes is None
        assert update.all_contacts_delay_minutes is None

    def test_partial_update_reminder(self):
        update = EscalationConfigUpdate(reminder_delay_minutes=3)
        assert update.reminder_delay_minutes == 3
        assert update.primary_contact_delay_minutes is None

    def test_partial_update_primary(self):
        update = EscalationConfigUpdate(primary_contact_delay_minutes=15)
        assert update.primary_contact_delay_minutes == 15

    def test_valid_ordering(self):
        update = EscalationConfigUpdate(
            reminder_delay_minutes=3,
            primary_contact_delay_minutes=8,
            all_contacts_delay_minutes=15,
        )
        assert update.reminder_delay_minutes == 3
        assert update.all_contacts_delay_minutes == 15

    def test_reminder_ge_primary_fails(self):
        with pytest.raises(
            ValidationError,
            match="reminder_delay_minutes must be less than primary_contact_delay_minutes",
        ):
            EscalationConfigUpdate(
                reminder_delay_minutes=10,
                primary_contact_delay_minutes=5,
            )

    def test_reminder_equal_primary_fails(self):
        with pytest.raises(
            ValidationError,
            match="reminder_delay_minutes must be less than primary_contact_delay_minutes",
        ):
            EscalationConfigUpdate(
                reminder_delay_minutes=10,
                primary_contact_delay_minutes=10,
            )

    def test_primary_ge_all_contacts_fails(self):
        with pytest.raises(
            ValidationError,
            match="primary_contact_delay_minutes must be less than all_contacts_delay_minutes",
        ):
            EscalationConfigUpdate(
                primary_contact_delay_minutes=25,
                all_contacts_delay_minutes=20,
            )

    def test_reminder_ge_all_contacts_fails(self):
        with pytest.raises(
            ValidationError,
            match="reminder_delay_minutes must be less than all_contacts_delay_minutes",
        ):
            EscalationConfigUpdate(
                reminder_delay_minutes=30,
                all_contacts_delay_minutes=20,
            )

    def test_below_minimum_fails(self):
        with pytest.raises(ValidationError):
            EscalationConfigUpdate(reminder_delay_minutes=1)

    def test_above_maximum_reminder_fails(self):
        with pytest.raises(ValidationError):
            EscalationConfigUpdate(reminder_delay_minutes=61)

    def test_above_maximum_primary_fails(self):
        with pytest.raises(ValidationError):
            EscalationConfigUpdate(primary_contact_delay_minutes=121)

    def test_above_maximum_all_contacts_fails(self):
        with pytest.raises(ValidationError):
            EscalationConfigUpdate(all_contacts_delay_minutes=241)

    def test_minimum_value_accepted(self):
        update = EscalationConfigUpdate(reminder_delay_minutes=2)
        assert update.reminder_delay_minutes == 2

    def test_maximum_values_accepted(self):
        update = EscalationConfigUpdate(
            reminder_delay_minutes=60,
            primary_contact_delay_minutes=120,
            all_contacts_delay_minutes=240,
        )
        assert update.reminder_delay_minutes == 60
        assert update.primary_contact_delay_minutes == 120
        assert update.all_contacts_delay_minutes == 240


class TestEscalationConfigDefaults:
    """Tests for EscalationConfigDefaults schema."""

    def test_default_values(self):
        defaults = EscalationConfigDefaults()
        assert defaults.reminder_delay_minutes == 5
        assert defaults.primary_contact_delay_minutes == 10
        assert defaults.all_contacts_delay_minutes == 20


# ── Service tests ──


class TestGetOrCreateConfig:
    """Tests for get_or_create_config service function."""

    @pytest.mark.asyncio
    async def test_creates_defaults_when_none_exist(self):
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

    @pytest.mark.asyncio
    async def test_concurrent_create_integrity_error_fallback(self):
        """When a concurrent request already created the row, fallback to fetch."""
        user_id = uuid.uuid4()
        fetched = MagicMock()
        fetched.user_id = user_id

        # First execute: no existing row; second execute: returns the row
        first_result = MagicMock()
        first_result.scalar_one_or_none.return_value = None

        second_result = MagicMock()
        second_result.scalar_one.return_value = fetched

        mock_db = AsyncMock()
        mock_db.execute.side_effect = [first_result, second_result]
        mock_db.commit = AsyncMock(side_effect=IntegrityError("dup", {}, Exception()))
        mock_db.rollback = AsyncMock()

        result = await get_or_create_config(user_id, mock_db)

        assert result == fetched
        mock_db.rollback.assert_called_once()
        mock_db.refresh.assert_not_called()


class TestUpdateConfig:
    """Tests for update_config service function."""

    @pytest.mark.asyncio
    async def test_partial_update_applies_fields(self):
        user_id = uuid.uuid4()
        existing = MagicMock()
        existing.user_id = user_id
        existing.reminder_delay_minutes = 5
        existing.primary_contact_delay_minutes = 10
        existing.all_contacts_delay_minutes = 20

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        updates = EscalationConfigUpdate(reminder_delay_minutes=3)
        result = await update_config(user_id, updates, mock_db)

        assert result.reminder_delay_minutes == 3
        mock_db.commit.assert_called_once()

    @pytest.mark.asyncio
    async def test_ordering_violation_reminder_ge_primary(self):
        user_id = uuid.uuid4()
        existing = MagicMock()
        existing.user_id = user_id
        existing.reminder_delay_minutes = 5
        existing.primary_contact_delay_minutes = 10
        existing.all_contacts_delay_minutes = 20

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        updates = EscalationConfigUpdate(reminder_delay_minutes=15)

        with pytest.raises(
            ValueError,
            match="reminder_delay_minutes.*must be less than.*primary_contact_delay_minutes",
        ):
            await update_config(user_id, updates, mock_db)

    @pytest.mark.asyncio
    async def test_ordering_violation_primary_ge_all(self):
        user_id = uuid.uuid4()
        existing = MagicMock()
        existing.user_id = user_id
        existing.reminder_delay_minutes = 5
        existing.primary_contact_delay_minutes = 10
        existing.all_contacts_delay_minutes = 20

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        updates = EscalationConfigUpdate(primary_contact_delay_minutes=25)

        with pytest.raises(
            ValueError,
            match="primary_contact_delay_minutes.*must be less than.*all_contacts_delay_minutes",
        ):
            await update_config(user_id, updates, mock_db)

    @pytest.mark.asyncio
    async def test_single_field_update_succeeds(self):
        user_id = uuid.uuid4()
        existing = MagicMock()
        existing.user_id = user_id
        existing.reminder_delay_minutes = 5
        existing.primary_contact_delay_minutes = 10
        existing.all_contacts_delay_minutes = 20

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        updates = EscalationConfigUpdate(all_contacts_delay_minutes=30)
        result = await update_config(user_id, updates, mock_db)

        assert result.all_contacts_delay_minutes == 30
        mock_db.commit.assert_called_once()


# ── Endpoint tests ──


class TestGetEscalationConfigEndpoint:
    """Tests for GET /api/settings/escalation-config."""

    @pytest.mark.asyncio
    async def test_unauthenticated_returns_401(self, client):
        response = await client.get("/api/settings/escalation-config")
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_authenticated_returns_defaults(self, client):
        cookie = await register_and_login(client)
        response = await client.get(
            "/api/settings/escalation-config",
            cookies={settings.jwt_cookie_name: cookie},
        )
        assert response.status_code == 200
        data = response.json()
        assert data["reminder_delay_minutes"] == 5
        assert data["primary_contact_delay_minutes"] == 10
        assert data["all_contacts_delay_minutes"] == 20
        assert "id" in data
        assert "updated_at" in data

    @pytest.mark.asyncio
    async def test_idempotent_get_or_create(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        r1 = await client.get("/api/settings/escalation-config", cookies=cookies)
        r2 = await client.get("/api/settings/escalation-config", cookies=cookies)

        assert r1.json()["id"] == r2.json()["id"]


class TestPatchEscalationConfigEndpoint:
    """Tests for PATCH /api/settings/escalation-config."""

    @pytest.mark.asyncio
    async def test_unauthenticated_returns_401(self, client):
        response = await client.patch(
            "/api/settings/escalation-config",
            json={"reminder_delay_minutes": 3},
        )
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_below_minimum_returns_422(self, client):
        cookie = await register_and_login(client)
        response = await client.patch(
            "/api/settings/escalation-config",
            json={"reminder_delay_minutes": 1},
            cookies={settings.jwt_cookie_name: cookie},
        )
        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_ordering_violation_returns_422(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        # Ensure config exists
        await client.get("/api/settings/escalation-config", cookies=cookies)

        # Set reminder above default primary_contact (10)
        response = await client.patch(
            "/api/settings/escalation-config",
            json={"reminder_delay_minutes": 15},
            cookies=cookies,
        )
        assert response.status_code == 422
        assert "reminder_delay_minutes" in response.json()["detail"]

    @pytest.mark.asyncio
    async def test_valid_partial_update(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.patch(
            "/api/settings/escalation-config",
            json={"reminder_delay_minutes": 3},
            cookies=cookies,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["reminder_delay_minutes"] == 3
        assert data["primary_contact_delay_minutes"] == 10
        assert data["all_contacts_delay_minutes"] == 20

    @pytest.mark.asyncio
    async def test_update_all_fields(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.patch(
            "/api/settings/escalation-config",
            json={
                "reminder_delay_minutes": 3,
                "primary_contact_delay_minutes": 8,
                "all_contacts_delay_minutes": 15,
            },
            cookies=cookies,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["reminder_delay_minutes"] == 3
        assert data["primary_contact_delay_minutes"] == 8
        assert data["all_contacts_delay_minutes"] == 15

    @pytest.mark.asyncio
    async def test_persists_across_requests(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        await client.patch(
            "/api/settings/escalation-config",
            json={"all_contacts_delay_minutes": 30},
            cookies=cookies,
        )

        response = await client.get("/api/settings/escalation-config", cookies=cookies)
        assert response.json()["all_contacts_delay_minutes"] == 30

    @pytest.mark.asyncio
    async def test_empty_body_returns_200(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.patch(
            "/api/settings/escalation-config",
            json={},
            cookies=cookies,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["reminder_delay_minutes"] == 5
        assert data["primary_contact_delay_minutes"] == 10


class TestGetEscalationConfigDefaultsEndpoint:
    """Tests for GET /api/settings/escalation-config/defaults."""

    @pytest.mark.asyncio
    async def test_returns_defaults_without_auth(self, client):
        response = await client.get("/api/settings/escalation-config/defaults")
        assert response.status_code == 200
        data = response.json()
        assert data["reminder_delay_minutes"] == 5
        assert data["primary_contact_delay_minutes"] == 10
        assert data["all_contacts_delay_minutes"] == 20
