"""Story 6.5: Tests for emergency contact schemas, service, and endpoints."""

import uuid
from unittest.mock import AsyncMock, MagicMock

import pytest
from pydantic import ValidationError

from src.config import settings
from src.models.emergency_contact import ContactPriority
from src.schemas.emergency_contact import (
    EmergencyContactCreate,
    EmergencyContactUpdate,
)
from src.services.emergency_contact import (
    MAX_CONTACTS_PER_USER,
    create_contact,
    delete_contact,
    get_contact,
    list_contacts,
)


def unique_email(prefix: str = "test") -> str:
    """Generate a unique email for testing."""
    return f"{prefix}_{uuid.uuid4().hex[:8]}@example.com"


async def register_and_login(client) -> str:
    """Register a new user and return the session cookie value."""
    email = unique_email("ec")
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


class TestEmergencyContactCreate:
    """Tests for EmergencyContactCreate schema validation."""

    def test_valid_create(self):
        data = EmergencyContactCreate(
            name="Mom",
            telegram_username="mom_user",
            priority=ContactPriority.PRIMARY,
        )
        assert data.name == "Mom"
        assert data.telegram_username == "mom_user"
        assert data.priority == ContactPriority.PRIMARY

    def test_strips_at_prefix(self):
        data = EmergencyContactCreate(
            name="Mom",
            telegram_username="@mom_user",
            priority=ContactPriority.PRIMARY,
        )
        assert data.telegram_username == "mom_user"

    def test_strips_name_whitespace(self):
        data = EmergencyContactCreate(
            name="  Mom  ",
            telegram_username="mom_user",
            priority=ContactPriority.PRIMARY,
        )
        assert data.name == "Mom"

    def test_name_too_long(self):
        with pytest.raises(ValidationError, match="String should have at most 100"):
            EmergencyContactCreate(
                name="x" * 101,
                telegram_username="mom_user",
                priority=ContactPriority.PRIMARY,
            )

    def test_name_empty(self):
        with pytest.raises(ValidationError):
            EmergencyContactCreate(
                name="",
                telegram_username="mom_user",
                priority=ContactPriority.PRIMARY,
            )

    def test_name_whitespace_only(self):
        with pytest.raises(ValidationError, match="empty or whitespace"):
            EmergencyContactCreate(
                name="   ",
                telegram_username="mom_user",
                priority=ContactPriority.PRIMARY,
            )

    def test_telegram_username_too_short(self):
        with pytest.raises(ValidationError):
            EmergencyContactCreate(
                name="Mom",
                telegram_username="abc",
                priority=ContactPriority.PRIMARY,
            )

    def test_telegram_username_invalid_chars(self):
        with pytest.raises(ValidationError, match="alphanumeric"):
            EmergencyContactCreate(
                name="Mom",
                telegram_username="bad-user!",
                priority=ContactPriority.PRIMARY,
            )

    def test_telegram_username_too_long(self):
        with pytest.raises(ValidationError):
            EmergencyContactCreate(
                name="Mom",
                telegram_username="a" * 33,
                priority=ContactPriority.PRIMARY,
            )

    def test_valid_secondary_priority(self):
        data = EmergencyContactCreate(
            name="Brother",
            telegram_username="bro_user",
            priority=ContactPriority.SECONDARY,
        )
        assert data.priority == ContactPriority.SECONDARY

    def test_invalid_priority(self):
        with pytest.raises(ValidationError):
            EmergencyContactCreate(
                name="Mom",
                telegram_username="mom_user",
                priority="invalid",
            )


class TestEmergencyContactUpdate:
    """Tests for EmergencyContactUpdate schema validation."""

    def test_all_none_is_valid(self):
        update = EmergencyContactUpdate()
        assert update.name is None
        assert update.telegram_username is None
        assert update.priority is None

    def test_partial_update_name(self):
        update = EmergencyContactUpdate(name="Dad")
        assert update.name == "Dad"
        assert update.telegram_username is None

    def test_partial_update_telegram(self):
        update = EmergencyContactUpdate(telegram_username="@new_user")
        assert update.telegram_username == "new_user"

    def test_invalid_telegram_format(self):
        with pytest.raises(ValidationError, match="alphanumeric"):
            EmergencyContactUpdate(telegram_username="bad user!")


# ── Service tests ──


class TestListContacts:
    """Tests for list_contacts service function."""

    @pytest.mark.asyncio
    async def test_returns_empty_list(self):
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = []

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        contacts = await list_contacts(uuid.uuid4(), mock_db)
        assert contacts == []

    @pytest.mark.asyncio
    async def test_returns_contacts(self):
        c1 = MagicMock()
        c2 = MagicMock()
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = [c1, c2]

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        contacts = await list_contacts(uuid.uuid4(), mock_db)
        assert len(contacts) == 2


class TestCreateContact:
    """Tests for create_contact service function."""

    @pytest.mark.asyncio
    async def test_create_success(self):
        user_id = uuid.uuid4()
        data = EmergencyContactCreate(
            name="Mom",
            telegram_username="mom_user",
            priority=ContactPriority.PRIMARY,
        )

        # Mock list returning empty (under limit)
        mock_list_result = MagicMock()
        mock_list_result.scalars.return_value.all.return_value = []

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_list_result
        mock_db.commit = AsyncMock()
        mock_db.refresh = AsyncMock()

        contact = await create_contact(user_id, data, mock_db)
        assert contact.name == "Mom"
        assert contact.telegram_username == "mom_user"
        assert contact.position == 0
        mock_db.add.assert_called_once()
        mock_db.commit.assert_called_once()

    @pytest.mark.asyncio
    async def test_create_at_limit_raises(self):
        user_id = uuid.uuid4()
        data = EmergencyContactCreate(
            name="Fourth",
            telegram_username="fourth_user",
            priority=ContactPriority.SECONDARY,
        )

        # Mock list returning MAX contacts
        existing = [MagicMock() for _ in range(MAX_CONTACTS_PER_USER)]
        mock_list_result = MagicMock()
        mock_list_result.scalars.return_value.all.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_list_result

        with pytest.raises(ValueError, match="Maximum of 3"):
            await create_contact(user_id, data, mock_db)


class TestGetContact:
    """Tests for get_contact service function."""

    @pytest.mark.asyncio
    async def test_not_found(self):
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = None

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        result = await get_contact(uuid.uuid4(), uuid.uuid4(), mock_db)
        assert result is None


class TestDeleteContact:
    """Tests for delete_contact service function."""

    @pytest.mark.asyncio
    async def test_delete_not_found(self):
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = None

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result

        result = await delete_contact(uuid.uuid4(), uuid.uuid4(), mock_db)
        assert result is False

    @pytest.mark.asyncio
    async def test_delete_success(self):
        existing = MagicMock()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = existing

        mock_db = AsyncMock()
        mock_db.execute.return_value = mock_result
        mock_db.delete = AsyncMock()
        mock_db.commit = AsyncMock()

        result = await delete_contact(uuid.uuid4(), uuid.uuid4(), mock_db)
        assert result is True
        mock_db.delete.assert_called_once_with(existing)
        mock_db.commit.assert_called_once()


# ── Endpoint tests ──


class TestGetEmergencyContactsEndpoint:
    """Tests for GET /api/settings/emergency-contacts."""

    @pytest.mark.asyncio
    async def test_unauthenticated_returns_401(self, client):
        response = await client.get("/api/settings/emergency-contacts")
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_authenticated_returns_empty_list(self, client):
        cookie = await register_and_login(client)
        response = await client.get(
            "/api/settings/emergency-contacts",
            cookies={settings.jwt_cookie_name: cookie},
        )
        assert response.status_code == 200
        data = response.json()
        assert data["contacts"] == []
        assert data["count"] == 0


class TestPostEmergencyContactEndpoint:
    """Tests for POST /api/settings/emergency-contacts."""

    @pytest.mark.asyncio
    async def test_unauthenticated_returns_401(self, client):
        response = await client.post(
            "/api/settings/emergency-contacts",
            json={
                "name": "Mom",
                "telegram_username": "mom_user",
                "priority": "primary",
            },
        )
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_create_contact(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        response = await client.post(
            "/api/settings/emergency-contacts",
            json={
                "name": "Mom",
                "telegram_username": "@mom_user",
                "priority": "primary",
            },
            cookies=cookies,
        )
        assert response.status_code == 201
        data = response.json()
        assert data["name"] == "Mom"
        assert data["telegram_username"] == "mom_user"
        assert data["priority"] == "primary"
        assert data["position"] == 0
        assert "id" in data
        assert "created_at" in data

    @pytest.mark.asyncio
    async def test_duplicate_telegram_returns_409(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        contact = {
            "name": "Mom",
            "telegram_username": "mom_user",
            "priority": "primary",
        }

        # First create succeeds
        r1 = await client.post(
            "/api/settings/emergency-contacts", json=contact, cookies=cookies
        )
        assert r1.status_code == 201

        # Second create with same telegram should fail
        r2 = await client.post(
            "/api/settings/emergency-contacts",
            json={**contact, "name": "Mother"},
            cookies=cookies,
        )
        assert r2.status_code == 409

    @pytest.mark.asyncio
    async def test_max_contacts_returns_409(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        for i in range(MAX_CONTACTS_PER_USER):
            r = await client.post(
                "/api/settings/emergency-contacts",
                json={
                    "name": f"Contact {i}",
                    "telegram_username": f"user_{i}_{uuid.uuid4().hex[:6]}",
                    "priority": "primary",
                },
                cookies=cookies,
            )
            assert r.status_code == 201

        # Fourth contact should fail
        r = await client.post(
            "/api/settings/emergency-contacts",
            json={
                "name": "Overflow",
                "telegram_username": "overflow_user",
                "priority": "secondary",
            },
            cookies=cookies,
        )
        assert r.status_code == 409
        assert "Maximum" in r.json()["detail"]

    @pytest.mark.asyncio
    async def test_invalid_telegram_returns_422(self, client):
        cookie = await register_and_login(client)
        response = await client.post(
            "/api/settings/emergency-contacts",
            json={
                "name": "Mom",
                "telegram_username": "ab",
                "priority": "primary",
            },
            cookies={settings.jwt_cookie_name: cookie},
        )
        assert response.status_code == 422


class TestPatchEmergencyContactEndpoint:
    """Tests for PATCH /api/settings/emergency-contacts/{id}."""

    @pytest.mark.asyncio
    async def test_update_contact(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        # Create contact
        r = await client.post(
            "/api/settings/emergency-contacts",
            json={
                "name": "Mom",
                "telegram_username": "mom_user",
                "priority": "primary",
            },
            cookies=cookies,
        )
        contact_id = r.json()["id"]

        # Update name
        r2 = await client.patch(
            f"/api/settings/emergency-contacts/{contact_id}",
            json={"name": "Mother"},
            cookies=cookies,
        )
        assert r2.status_code == 200
        assert r2.json()["name"] == "Mother"
        assert r2.json()["telegram_username"] == "mom_user"

    @pytest.mark.asyncio
    async def test_not_found_returns_404(self, client):
        cookie = await register_and_login(client)
        fake_id = str(uuid.uuid4())
        response = await client.patch(
            f"/api/settings/emergency-contacts/{fake_id}",
            json={"name": "Nobody"},
            cookies={settings.jwt_cookie_name: cookie},
        )
        assert response.status_code == 404

    @pytest.mark.asyncio
    async def test_owner_isolation(self, client):
        """User A cannot update User B's contact."""
        cookie_a = await register_and_login(client)
        cookie_b = await register_and_login(client)

        # User A creates contact
        r = await client.post(
            "/api/settings/emergency-contacts",
            json={
                "name": "Mom",
                "telegram_username": "mom_user",
                "priority": "primary",
            },
            cookies={settings.jwt_cookie_name: cookie_a},
        )
        contact_id = r.json()["id"]

        # User B tries to update it
        r2 = await client.patch(
            f"/api/settings/emergency-contacts/{contact_id}",
            json={"name": "Hacked"},
            cookies={settings.jwt_cookie_name: cookie_b},
        )
        assert r2.status_code == 404


class TestDeleteEmergencyContactEndpoint:
    """Tests for DELETE /api/settings/emergency-contacts/{id}."""

    @pytest.mark.asyncio
    async def test_delete_contact(self, client):
        cookie = await register_and_login(client)
        cookies = {settings.jwt_cookie_name: cookie}

        # Create contact
        r = await client.post(
            "/api/settings/emergency-contacts",
            json={
                "name": "Mom",
                "telegram_username": "mom_user",
                "priority": "primary",
            },
            cookies=cookies,
        )
        contact_id = r.json()["id"]

        # Delete it
        r2 = await client.delete(
            f"/api/settings/emergency-contacts/{contact_id}",
            cookies=cookies,
        )
        assert r2.status_code == 204

        # Verify it's gone
        r3 = await client.get(
            "/api/settings/emergency-contacts",
            cookies=cookies,
        )
        assert r3.json()["count"] == 0

    @pytest.mark.asyncio
    async def test_not_found_returns_404(self, client):
        cookie = await register_and_login(client)
        fake_id = str(uuid.uuid4())
        response = await client.delete(
            f"/api/settings/emergency-contacts/{fake_id}",
            cookies={settings.jwt_cookie_name: cookie},
        )
        assert response.status_code == 404

    @pytest.mark.asyncio
    async def test_owner_isolation(self, client):
        """User A cannot delete User B's contact."""
        cookie_a = await register_and_login(client)
        cookie_b = await register_and_login(client)

        # User A creates contact
        r = await client.post(
            "/api/settings/emergency-contacts",
            json={
                "name": "Mom",
                "telegram_username": "mom_user",
                "priority": "primary",
            },
            cookies={settings.jwt_cookie_name: cookie_a},
        )
        contact_id = r.json()["id"]

        # User B tries to delete it
        r2 = await client.delete(
            f"/api/settings/emergency-contacts/{contact_id}",
            cookies={settings.jwt_cookie_name: cookie_b},
        )
        assert r2.status_code == 404

        # Verify it still exists for User A
        r3 = await client.get(
            "/api/settings/emergency-contacts",
            cookies={settings.jwt_cookie_name: cookie_a},
        )
        assert r3.json()["count"] == 1
