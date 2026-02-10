"""Story 9.2: Tests for brief delivery configuration service and settings router."""

import uuid
from datetime import time
from unittest.mock import AsyncMock, MagicMock

import pytest
from pydantic import ValidationError

from src.config import settings
from src.schemas.brief_delivery_config import (
    BriefDeliveryConfigDefaults,
    BriefDeliveryConfigUpdate,
    DeliveryChannel,
)
from src.services.brief_delivery_config import (
    get_or_create_config,
    update_config,
)


def unique_email(prefix: str = "test") -> str:
    """Generate a unique email for testing."""
    return f"{prefix}_{uuid.uuid4().hex[:8]}@example.com"


async def register_and_login(client) -> str:
    """Register a new user and return the session cookie value."""
    email = unique_email("brief_delivery")
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


class TestBriefDeliveryConfigUpdate:
    """Tests for BriefDeliveryConfigUpdate schema validation."""

    def test_all_none_is_valid(self):
        update = BriefDeliveryConfigUpdate()
        assert update.enabled is None
        assert update.delivery_time is None
        assert update.timezone is None
        assert update.channel is None

    def test_partial_update_enabled(self):
        update = BriefDeliveryConfigUpdate(enabled=False)
        assert update.enabled is False

    def test_partial_update_delivery_time(self):
        update = BriefDeliveryConfigUpdate(delivery_time=time(8, 30))
        assert update.delivery_time == time(8, 30)

    def test_partial_update_timezone(self):
        update = BriefDeliveryConfigUpdate(timezone="America/New_York")
        assert update.timezone == "America/New_York"

    def test_partial_update_channel(self):
        update = BriefDeliveryConfigUpdate(channel=DeliveryChannel.TELEGRAM)
        assert update.channel == DeliveryChannel.TELEGRAM

    def test_invalid_timezone_fails(self):
        with pytest.raises(ValidationError, match="Invalid timezone"):
            BriefDeliveryConfigUpdate(timezone="Not/A/Timezone")

    def test_valid_timezone_passes(self):
        update = BriefDeliveryConfigUpdate(timezone="Europe/London")
        assert update.timezone == "Europe/London"

    def test_all_channels_valid(self):
        for ch in DeliveryChannel:
            update = BriefDeliveryConfigUpdate(channel=ch)
            assert update.channel == ch


class TestBriefDeliveryConfigDefaults:
    """Tests for BriefDeliveryConfigDefaults schema."""

    def test_default_values(self):
        defaults = BriefDeliveryConfigDefaults()
        assert defaults.enabled is True
        assert defaults.delivery_time == time(7, 0)
        assert defaults.timezone == "UTC"
        assert defaults.channel == DeliveryChannel.BOTH


# ── Service tests ──


class TestGetOrCreateConfig:
    """Tests for get_or_create_config service function."""

    @pytest.mark.asyncio
    async def test_creates_defaults_when_none_exist(self):
        """Should create a new BriefDeliveryConfig with defaults."""
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
    async def test_partial_update_enabled(self):
        """Should only update enabled."""
        user_id = uuid.uuid4()
        existing = MagicMock()
        existing.user_id = user_id

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        updates = BriefDeliveryConfigUpdate(enabled=False)
        result = await update_config(user_id, updates, mock_db)

        assert result.enabled is False
        mock_db.commit.assert_called_once()

    @pytest.mark.asyncio
    async def test_partial_update_channel(self):
        """Should only update channel."""
        user_id = uuid.uuid4()
        existing = MagicMock()
        existing.user_id = user_id

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        updates = BriefDeliveryConfigUpdate(channel=DeliveryChannel.WEB_ONLY)
        result = await update_config(user_id, updates, mock_db)

        assert result.channel == DeliveryChannel.WEB_ONLY
        mock_db.commit.assert_called_once()


# ── Endpoint tests ──


class TestGetBriefDeliveryConfigEndpoint:
    """Tests for GET /api/settings/brief-delivery."""

    @pytest.mark.asyncio
    async def test_unauthenticated_returns_401(self, client):
        response = await client.get("/api/settings/brief-delivery")
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_authenticated_returns_defaults(self, client):
        cookie = await register_and_login(client)
        response = await client.get(
            "/api/settings/brief-delivery",
            cookies={settings.jwt_cookie_name: cookie},
        )
        assert response.status_code == 200
        data = response.json()
        assert data["enabled"] is True
        assert data["delivery_time"] == "07:00:00"
        assert data["timezone"] == "UTC"
        assert data["channel"] == "both"
        assert "id" in data
        assert "updated_at" in data

    @pytest.mark.asyncio
    async def test_idempotent_get_or_create(self, client):
        """Calling GET twice should return the same record."""
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        r1 = await client.get("/api/settings/brief-delivery", cookies=cookies)
        r2 = await client.get("/api/settings/brief-delivery", cookies=cookies)

        assert r1.json()["id"] == r2.json()["id"]


class TestPatchBriefDeliveryConfigEndpoint:
    """Tests for PATCH /api/settings/brief-delivery."""

    @pytest.mark.asyncio
    async def test_unauthenticated_returns_401(self, client):
        response = await client.patch(
            "/api/settings/brief-delivery",
            json={"enabled": False},
        )
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_invalid_timezone_returns_422(self, client):
        cookie = await register_and_login(client)
        response = await client.patch(
            "/api/settings/brief-delivery",
            json={"timezone": "Not/A/Timezone"},
            cookies={settings.jwt_cookie_name: cookie},
        )
        assert response.status_code == 422

    @pytest.mark.asyncio
    async def test_update_enabled(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.patch(
            "/api/settings/brief-delivery",
            json={"enabled": False},
            cookies=cookies,
        )
        assert response.status_code == 200
        assert response.json()["enabled"] is False

    @pytest.mark.asyncio
    async def test_update_delivery_time(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.patch(
            "/api/settings/brief-delivery",
            json={"delivery_time": "08:30:00"},
            cookies=cookies,
        )
        assert response.status_code == 200
        assert response.json()["delivery_time"] == "08:30:00"

    @pytest.mark.asyncio
    async def test_update_timezone(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.patch(
            "/api/settings/brief-delivery",
            json={"timezone": "America/New_York"},
            cookies=cookies,
        )
        assert response.status_code == 200
        assert response.json()["timezone"] == "America/New_York"

    @pytest.mark.asyncio
    async def test_update_channel(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.patch(
            "/api/settings/brief-delivery",
            json={"channel": "telegram"},
            cookies=cookies,
        )
        assert response.status_code == 200
        assert response.json()["channel"] == "telegram"

    @pytest.mark.asyncio
    async def test_update_all_fields(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.patch(
            "/api/settings/brief-delivery",
            json={
                "enabled": False,
                "delivery_time": "22:00:00",
                "timezone": "Europe/London",
                "channel": "web_only",
            },
            cookies=cookies,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["enabled"] is False
        assert data["delivery_time"] == "22:00:00"
        assert data["timezone"] == "Europe/London"
        assert data["channel"] == "web_only"

    @pytest.mark.asyncio
    async def test_persists_across_requests(self, client):
        """Updated values should persist when fetched again."""
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        await client.patch(
            "/api/settings/brief-delivery",
            json={"timezone": "Asia/Tokyo"},
            cookies=cookies,
        )

        response = await client.get(
            "/api/settings/brief-delivery",
            cookies=cookies,
        )
        assert response.json()["timezone"] == "Asia/Tokyo"

    @pytest.mark.asyncio
    async def test_empty_body_returns_200(self, client):
        """Empty PATCH body should succeed without modifying anything."""
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.patch(
            "/api/settings/brief-delivery",
            json={},
            cookies=cookies,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["enabled"] is True
        assert data["timezone"] == "UTC"


class TestGetBriefDeliveryDefaultsEndpoint:
    """Tests for GET /api/settings/brief-delivery/defaults."""

    @pytest.mark.asyncio
    async def test_returns_defaults_without_auth(self, client):
        response = await client.get("/api/settings/brief-delivery/defaults")
        assert response.status_code == 200
        data = response.json()
        assert data["enabled"] is True
        assert data["delivery_time"] == "07:00:00"
        assert data["timezone"] == "UTC"
        assert data["channel"] == "both"
