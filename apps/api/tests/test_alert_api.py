"""Tests for alert API endpoints (Story 16.11)."""

import uuid
from datetime import UTC, datetime, timedelta
from unittest.mock import AsyncMock, MagicMock

import pytest
from httpx import ASGITransport, AsyncClient

from src.core.auth import get_current_user
from src.database import get_db
from src.main import app
from src.models.alert import AlertSeverity, AlertType
from src.models.user import UserRole


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
    user.role = UserRole(role)
    user.is_active = True
    return user


def _mock_alert(user_id=None):
    """Create a mock alert."""
    alert = MagicMock()
    alert.id = uuid.uuid4()
    alert.user_id = user_id or uuid.uuid4()
    alert.alert_type = AlertType.HIGH_WARNING
    alert.severity = AlertSeverity.WARNING
    alert.current_value = 250.0
    alert.predicted_value = 280.0
    alert.iob_value = 1.5
    alert.message = "High glucose warning"
    alert.trend_rate = 2.0
    alert.acknowledged = False
    alert.acknowledged_at = None
    alert.created_at = datetime.now(UTC)
    alert.expires_at = datetime.now(UTC) + timedelta(hours=1)
    return alert


class TestAlertApi:
    """Tests for /api/v1/alerts endpoints."""

    @pytest.mark.asyncio
    async def test_get_pending_alerts_requires_auth(self, client):
        """Pending alerts without auth returns 401."""
        response = await client.get("/api/v1/alerts/pending")
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_acknowledge_alert_requires_auth(self, client):
        """Acknowledge without auth returns 401."""
        alert_id = str(uuid.uuid4())
        response = await client.post(f"/api/v1/alerts/{alert_id}/acknowledge")
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_acknowledge_alert_not_found(self, client):
        """Acknowledging nonexistent alert returns 404."""
        mock_user = _mock_user()
        alert_id = str(uuid.uuid4())

        mock_db = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = None
        mock_db.execute = AsyncMock(return_value=mock_result)

        app.dependency_overrides[get_current_user] = lambda: mock_user
        app.dependency_overrides[get_db] = lambda: mock_db

        try:
            response = await client.post(f"/api/v1/alerts/{alert_id}/acknowledge")
            assert response.status_code == 404
        finally:
            app.dependency_overrides.clear()

    @pytest.mark.asyncio
    async def test_acknowledge_own_alert_success(self, client):
        """User can acknowledge their own alert."""
        mock_user = _mock_user()
        alert = _mock_alert(user_id=mock_user.id)

        mock_db = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = alert
        mock_db.execute = AsyncMock(return_value=mock_result)
        mock_db.commit = AsyncMock()

        app.dependency_overrides[get_current_user] = lambda: mock_user
        app.dependency_overrides[get_db] = lambda: mock_db

        try:
            response = await client.post(f"/api/v1/alerts/{alert.id}/acknowledge")
            assert response.status_code == 200
            data = response.json()
            assert data["status"] == "acknowledged"
        finally:
            app.dependency_overrides.clear()

    @pytest.mark.asyncio
    async def test_acknowledge_others_alert_forbidden(self, client):
        """User cannot acknowledge another user's alert."""
        mock_user = _mock_user()
        other_user_id = uuid.uuid4()
        alert = _mock_alert(user_id=other_user_id)

        mock_db = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = alert
        mock_db.execute = AsyncMock(return_value=mock_result)

        app.dependency_overrides[get_current_user] = lambda: mock_user
        app.dependency_overrides[get_db] = lambda: mock_db

        try:
            response = await client.post(f"/api/v1/alerts/{alert.id}/acknowledge")
            assert response.status_code == 403
        finally:
            app.dependency_overrides.clear()
