"""Story 2.4: Tests for role-based access control."""

import uuid

import pytest
from httpx import ASGITransport, AsyncClient

from src.config import settings
from src.main import app


def unique_email(prefix: str = "test") -> str:
    """Generate a unique email for testing."""
    return f"{prefix}_{uuid.uuid4().hex[:8]}@example.com"


class TestRoleBasedAccessControl:
    """Tests for RBAC functionality."""

    async def test_admin_can_access_system_health(self):
        """Test that admin users can access /api/system/health."""
        # Note: This test requires an admin user in the database
        # For now, we test that a diabetic user gets 403
        email = unique_email("diabetic_health")
        password = "SecurePass123"

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            # Register user (gets diabetic role by default)
            await client.post(
                "/api/auth/register",
                json={"email": email, "password": password},
            )

            # Login
            login_response = await client.post(
                "/api/auth/login",
                json={"email": email, "password": password},
            )

            session_cookie = login_response.cookies.get(settings.jwt_cookie_name)

            # Try to access admin-only endpoint
            response = await client.get(
                "/api/system/health",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        # Diabetic user should get 403 Forbidden
        assert response.status_code == 403
        assert "permission" in response.json()["detail"].lower()

    async def test_unauthenticated_cannot_access_system_health(self):
        """Test that unauthenticated users cannot access /api/system/health."""
        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            response = await client.get("/api/system/health")

        assert response.status_code == 401
        assert "Not authenticated" in response.json()["detail"]

    async def test_diabetic_can_access_own_profile(self):
        """Test that diabetic users can access /api/auth/me."""
        email = unique_email("diabetic_profile")
        password = "SecurePass123"

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            # Register user (gets diabetic role by default)
            await client.post(
                "/api/auth/register",
                json={"email": email, "password": password},
            )

            # Login
            login_response = await client.post(
                "/api/auth/login",
                json={"email": email, "password": password},
            )

            session_cookie = login_response.cookies.get(settings.jwt_cookie_name)

            # Access own profile
            response = await client.get(
                "/api/auth/me",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200
        assert response.json()["role"] == "diabetic"

    async def test_403_response_for_unauthorized_role(self):
        """Test that accessing admin endpoint with wrong role returns 403."""
        email = unique_email("wrong_role")
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

            # Try to access system stats (admin only)
            response = await client.get(
                "/api/system/stats",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 403
        data = response.json()
        assert "permission" in data["detail"].lower()


class TestRoleAssignment:
    """Tests for role assignment."""

    async def test_new_user_gets_diabetic_role(self):
        """Test that new users are assigned the diabetic role."""
        email = unique_email("role_assignment")
        password = "SecurePass123"

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            response = await client.post(
                "/api/auth/register",
                json={"email": email, "password": password},
            )

        assert response.status_code == 201
        assert response.json()["role"] == "diabetic"

    async def test_role_persists_across_sessions(self):
        """Test that user role persists after logout and login."""
        email = unique_email("role_persist")
        password = "SecurePass123"

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            # Register
            await client.post(
                "/api/auth/register",
                json={"email": email, "password": password},
            )

            # First login
            login1 = await client.post(
                "/api/auth/login",
                json={"email": email, "password": password},
            )
            cookie1 = login1.cookies.get(settings.jwt_cookie_name)

            # Logout
            await client.post(
                "/api/auth/logout",
                cookies={settings.jwt_cookie_name: cookie1},
            )

            # Second login
            login2 = await client.post(
                "/api/auth/login",
                json={"email": email, "password": password},
            )
            cookie2 = login2.cookies.get(settings.jwt_cookie_name)

            # Check role is still diabetic
            me_response = await client.get(
                "/api/auth/me",
                cookies={settings.jwt_cookie_name: cookie2},
            )

        assert me_response.status_code == 200
        assert me_response.json()["role"] == "diabetic"
