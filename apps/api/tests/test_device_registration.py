"""Tests for device registration endpoints (Story 16.11)."""

import uuid
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from httpx import ASGITransport, AsyncClient

from src.core.auth import get_current_user
from src.database import get_db
from src.main import app


@pytest.fixture
async def client():
    """Create async test client."""
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test"
    ) as ac:
        yield ac


def _mock_user(role="diabetic"):
    """Create a mock user."""
    user = MagicMock()
    user.id = uuid.uuid4()
    user.email = "test@example.com"
    user.role = MagicMock()
    user.role.value = role
    user.is_active = True
    return user


class TestDeviceRegistration:
    """Tests for /api/v1/devices endpoints."""

    @pytest.mark.asyncio
    async def test_register_device_success(self, client):
        """Device registration returns 200 with device info."""
        mock_user = _mock_user()
        mock_db = AsyncMock()
        mock_device = MagicMock()
        mock_device.id = uuid.uuid4()
        mock_device.device_token = "test-token-123"

        app.dependency_overrides[get_current_user] = lambda: mock_user
        app.dependency_overrides[get_db] = lambda: mock_db

        try:
            with patch(
                "src.routers.device_registration.register_device",
                new_callable=AsyncMock,
                return_value=mock_device,
            ):
                response = await client.post(
                    "/api/v1/devices/register",
                    json={
                        "device_token": "test-token-123",
                        "device_name": "Test Phone",
                        "platform": "android",
                    },
                )
                assert response.status_code == 200
                data = response.json()
                assert data["device_token"] == "test-token-123"
                assert "id" in data
        finally:
            app.dependency_overrides.clear()

    @pytest.mark.asyncio
    async def test_register_device_requires_auth(self, client):
        """Device registration without auth returns 401."""
        response = await client.post(
            "/api/v1/devices/register",
            json={
                "device_token": "test-token",
                "device_name": "Test Phone",
            },
        )
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_register_device_validates_fields(self, client):
        """Device registration with empty fields returns 422."""
        mock_user = _mock_user()
        mock_db = AsyncMock()

        app.dependency_overrides[get_current_user] = lambda: mock_user
        app.dependency_overrides[get_db] = lambda: mock_db

        try:
            response = await client.post(
                "/api/v1/devices/register",
                json={
                    "device_token": "",
                    "device_name": "",
                },
            )
            assert response.status_code == 422
        finally:
            app.dependency_overrides.clear()

    @pytest.mark.asyncio
    async def test_unregister_device_success(self, client):
        """Device unregistration returns 204."""
        mock_user = _mock_user()
        mock_db = AsyncMock()

        app.dependency_overrides[get_current_user] = lambda: mock_user
        app.dependency_overrides[get_db] = lambda: mock_db

        try:
            with patch(
                "src.routers.device_registration.unregister_device",
                new_callable=AsyncMock,
                return_value=True,
            ):
                response = await client.delete("/api/v1/devices/test-token-123")
                assert response.status_code == 204
        finally:
            app.dependency_overrides.clear()

    @pytest.mark.asyncio
    async def test_unregister_device_not_found(self, client):
        """Unregistering unknown device returns 404."""
        mock_user = _mock_user()
        mock_db = AsyncMock()

        app.dependency_overrides[get_current_user] = lambda: mock_user
        app.dependency_overrides[get_db] = lambda: mock_db

        try:
            with patch(
                "src.routers.device_registration.unregister_device",
                new_callable=AsyncMock,
                return_value=False,
            ):
                response = await client.delete("/api/v1/devices/nonexistent")
                assert response.status_code == 404
        finally:
            app.dependency_overrides.clear()
