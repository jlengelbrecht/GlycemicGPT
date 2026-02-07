"""Tests for disclaimer acknowledgment API.

Story 1.3: First-Run Safety Disclaimer
- AC1: Given I am a new user who has never acknowledged the disclaimer,
       When I access the web application,
       Then I see a modal with safety disclaimers
- AC2: I must check two acknowledgment checkboxes
- AC3: I must click "I Understand & Accept" to proceed
- AC4: My acknowledgment is stored in the database with timestamp
"""

import uuid
from datetime import UTC
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from httpx import ASGITransport, AsyncClient

from src.main import app


@pytest.fixture
async def client():
    """Create async test client."""
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test"
    ) as ac:
        yield ac


class TestDisclaimerStatus:
    """Tests for GET /api/disclaimer/status endpoint."""

    @pytest.mark.asyncio
    async def test_returns_not_acknowledged_for_new_session(self, client):
        """
        Status returns acknowledged=False for a session that has not
        acknowledged the disclaimer.
        """
        session_id = str(uuid.uuid4())

        with patch(
            "src.routers.disclaimer.get_session_maker"
        ) as mock_get_session_maker:
            mock_session = AsyncMock()
            mock_result = MagicMock()
            mock_result.scalar_one_or_none.return_value = None
            mock_session.execute = AsyncMock(return_value=mock_result)
            mock_session.__aenter__ = AsyncMock(return_value=mock_session)
            mock_session.__aexit__ = AsyncMock(return_value=None)
            # get_session_maker() returns a factory, factory() returns session context
            mock_get_session_maker.return_value.return_value = mock_session

            response = await client.get(
                f"/api/disclaimer/status?session_id={session_id}"
            )

            assert response.status_code == 200
            data = response.json()
            assert data["acknowledged"] is False
            assert data["acknowledged_at"] is None
            assert data["disclaimer_version"] == "1.0"

    @pytest.mark.asyncio
    async def test_returns_acknowledged_for_existing_session(self, client):
        """
        Status returns acknowledged=True for a session that has
        previously acknowledged the disclaimer.
        """
        from datetime import datetime

        session_id = str(uuid.uuid4())
        acknowledged_at = datetime.now(UTC)

        with patch(
            "src.routers.disclaimer.get_session_maker"
        ) as mock_get_session_maker:
            mock_acknowledgment = MagicMock()
            mock_acknowledgment.acknowledged_at = acknowledged_at
            mock_acknowledgment.disclaimer_version = "1.0"

            mock_session = AsyncMock()
            mock_result = MagicMock()
            mock_result.scalar_one_or_none.return_value = mock_acknowledgment
            mock_session.execute = AsyncMock(return_value=mock_result)
            mock_session.__aenter__ = AsyncMock(return_value=mock_session)
            mock_session.__aexit__ = AsyncMock(return_value=None)
            mock_get_session_maker.return_value.return_value = mock_session

            response = await client.get(
                f"/api/disclaimer/status?session_id={session_id}"
            )

            assert response.status_code == 200
            data = response.json()
            assert data["acknowledged"] is True
            assert data["acknowledged_at"] is not None
            assert data["disclaimer_version"] == "1.0"


class TestDisclaimerAcknowledge:
    """Tests for POST /api/disclaimer/acknowledge endpoint."""

    @pytest.mark.asyncio
    async def test_requires_both_checkboxes_checked(self, client):
        """
        AC2: User must check both acknowledgment checkboxes.
        Returns 400 if not both checked.
        """
        session_id = str(uuid.uuid4())

        # Only one checkbox checked
        response = await client.post(
            "/api/disclaimer/acknowledge",
            json={
                "session_id": session_id,
                "checkbox_experimental": True,
                "checkbox_not_medical_advice": False,
            },
        )

        assert response.status_code == 400
        data = response.json()
        assert "both" in data["detail"].lower()

    @pytest.mark.asyncio
    async def test_stores_acknowledgment_with_timestamp(self, client):
        """
        AC4: Acknowledgment is stored in the database with timestamp.
        """
        from datetime import datetime

        session_id = str(uuid.uuid4())
        acknowledged_at = datetime.now(UTC)

        with patch(
            "src.routers.disclaimer.get_session_maker"
        ) as mock_get_session_maker:
            # First check returns None (not acknowledged)
            mock_session = AsyncMock()
            mock_result = MagicMock()
            mock_result.scalar_one_or_none.return_value = None
            mock_session.execute = AsyncMock(return_value=mock_result)

            # Create mock acknowledgment after save
            mock_acknowledgment = MagicMock()
            mock_acknowledgment.acknowledged_at = acknowledged_at

            async def mock_refresh(obj):
                obj.acknowledged_at = acknowledged_at

            mock_session.add = MagicMock()
            mock_session.commit = AsyncMock()
            mock_session.refresh = mock_refresh
            mock_session.__aenter__ = AsyncMock(return_value=mock_session)
            mock_session.__aexit__ = AsyncMock(return_value=None)
            mock_get_session_maker.return_value.return_value = mock_session

            response = await client.post(
                "/api/disclaimer/acknowledge",
                json={
                    "session_id": session_id,
                    "checkbox_experimental": True,
                    "checkbox_not_medical_advice": True,
                },
            )

            assert response.status_code == 200
            data = response.json()
            assert data["success"] is True
            assert data["acknowledged_at"] is not None
            assert "successfully" in data["message"].lower()


class TestDisclaimerContent:
    """Tests for GET /api/disclaimer/content endpoint."""

    @pytest.mark.asyncio
    async def test_returns_disclaimer_content(self, client):
        """
        AC1: Disclaimer content includes all required warnings.
        """
        response = await client.get("/api/disclaimer/content")

        assert response.status_code == 200
        data = response.json()

        assert data["version"] == "1.0"
        assert data["title"] == "Important Safety Information"

        # Check all required warnings are present
        warning_titles = [w["title"] for w in data["warnings"]]
        assert "Experimental Software" in warning_titles
        assert "AI Limitations" in warning_titles
        assert "Not FDA Approved" in warning_titles
        assert "Consult Your Healthcare Provider" in warning_titles

        # Check warning text contains required phrases
        warning_texts = " ".join([w["text"] for w in data["warnings"]])
        assert "experimental" in warning_texts.lower()
        assert "ai" in warning_texts.lower()
        assert "fda" in warning_texts.lower()
        assert "healthcare provider" in warning_texts.lower()

    @pytest.mark.asyncio
    async def test_returns_two_checkboxes(self, client):
        """
        AC2: There are two acknowledgment checkboxes.
        """
        response = await client.get("/api/disclaimer/content")

        assert response.status_code == 200
        data = response.json()

        assert len(data["checkboxes"]) == 2
        checkbox_ids = [c["id"] for c in data["checkboxes"]]
        assert "checkbox_experimental" in checkbox_ids
        assert "checkbox_not_medical_advice" in checkbox_ids

    @pytest.mark.asyncio
    async def test_returns_accept_button(self, client):
        """
        AC3: There is an "I Understand & Accept" button.
        """
        response = await client.get("/api/disclaimer/content")

        assert response.status_code == 200
        data = response.json()

        assert "I Understand & Accept" in data["button_text"]
