"""Story 3.7: Tests for IoB projection engine.

Tests the insulin decay curve calculations and IoB projection endpoint.
"""

import uuid
from datetime import UTC, datetime, timedelta

import pytest
from httpx import ASGITransport, AsyncClient

from src.config import settings
from src.main import app
from src.services.iob_projection import (
    INSULIN_DIA_HOURS,
    calculate_insulin_remaining,
    calculate_iob_activity_curve,
    project_iob,
)


def unique_email(prefix: str) -> str:
    """Generate a unique email for tests."""
    return f"{prefix}_{uuid.uuid4().hex[:8]}@example.com"


class TestInsulinDecayCurve:
    """Tests for the insulin decay curve calculations."""

    def test_calculate_remaining_at_zero_hours(self):
        """At time 0, all insulin should remain."""
        remaining = calculate_insulin_remaining(0)
        assert remaining == 1.0

    def test_calculate_remaining_at_negative_hours(self):
        """Negative elapsed time should return full insulin."""
        remaining = calculate_insulin_remaining(-1)
        assert remaining == 1.0

    def test_calculate_remaining_at_dia(self):
        """At DIA (4 hours), no insulin should remain."""
        remaining = calculate_insulin_remaining(INSULIN_DIA_HOURS)
        assert remaining == 0.0

    def test_calculate_remaining_after_dia(self):
        """After DIA, no insulin should remain."""
        remaining = calculate_insulin_remaining(5.0)
        assert remaining == 0.0

    def test_calculate_remaining_at_half_dia(self):
        """At half DIA (2 hours), should have ~75% remaining (parabolic decay)."""
        remaining = calculate_insulin_remaining(2.0)
        # t_ratio = 2/4 = 0.5, remaining = 1 - 0.5^2 = 0.75
        assert remaining == pytest.approx(0.75, rel=0.01)

    def test_calculate_remaining_at_one_hour(self):
        """At 1 hour, should have ~94% remaining."""
        remaining = calculate_insulin_remaining(1.0)
        # t_ratio = 1/4 = 0.25, remaining = 1 - 0.25^2 = 0.9375
        assert remaining == pytest.approx(0.9375, rel=0.01)

    def test_calculate_remaining_at_three_hours(self):
        """At 3 hours, should have ~44% remaining."""
        remaining = calculate_insulin_remaining(3.0)
        # t_ratio = 3/4 = 0.75, remaining = 1 - 0.75^2 = 0.4375
        assert remaining == pytest.approx(0.4375, rel=0.01)

    def test_calculate_remaining_with_custom_dia(self):
        """Test with a custom DIA value."""
        remaining = calculate_insulin_remaining(3.0, dia_hours=6.0)
        # t_ratio = 3/6 = 0.5, remaining = 1 - 0.5^2 = 0.75
        assert remaining == pytest.approx(0.75, rel=0.01)


class TestIoBActivityCurve:
    """Tests for the bilinear activity curve."""

    def test_activity_at_zero(self):
        """At time 0, all insulin activity remains."""
        activity = calculate_iob_activity_curve(0)
        assert activity == 1.0

    def test_activity_at_dia(self):
        """At DIA, no insulin activity remains."""
        activity = calculate_iob_activity_curve(INSULIN_DIA_HOURS)
        assert activity == 0.0

    def test_activity_at_peak(self):
        """At peak time (1 hour), should have ~80% remaining."""
        activity = calculate_iob_activity_curve(1.0)
        assert activity == pytest.approx(0.8, rel=0.01)

    def test_activity_decreases_over_time(self):
        """Activity should decrease monotonically over time."""
        times = [0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0]
        activities = [calculate_iob_activity_curve(t) for t in times]
        for i in range(len(activities) - 1):
            assert activities[i] >= activities[i + 1]


class TestProjectIoB:
    """Tests for the IoB projection function."""

    def test_project_iob_no_elapsed_time(self):
        """With no elapsed time, projected IoB equals confirmed."""
        now = datetime.now(UTC)
        projected = project_iob(2.5, now, now)
        assert projected == 2.5

    def test_project_iob_one_hour_elapsed(self):
        """After 1 hour, IoB should decay according to curve."""
        confirmed_at = datetime.now(UTC) - timedelta(hours=1)
        now = datetime.now(UTC)
        projected = project_iob(2.5, confirmed_at, now)
        # At 1 hour, ~93.75% should remain
        expected = 2.5 * 0.9375
        assert projected == pytest.approx(expected, rel=0.01)

    def test_project_iob_two_hours_elapsed(self):
        """After 2 hours, IoB should be ~75%."""
        confirmed_at = datetime.now(UTC) - timedelta(hours=2)
        now = datetime.now(UTC)
        projected = project_iob(2.5, confirmed_at, now)
        # At 2 hours, ~75% should remain
        expected = 2.5 * 0.75
        assert projected == pytest.approx(expected, rel=0.01)

    def test_project_iob_after_dia(self):
        """After DIA, IoB should be 0."""
        confirmed_at = datetime.now(UTC) - timedelta(hours=5)
        now = datetime.now(UTC)
        projected = project_iob(2.5, confirmed_at, now)
        assert projected == 0.0

    def test_project_iob_future_time(self):
        """Project IoB to a future time."""
        now = datetime.now(UTC)
        future = now + timedelta(hours=1)
        # Start with 2.5u now, project to 1 hour from now
        projected = project_iob(2.5, now, future)
        expected = 2.5 * 0.9375
        assert projected == pytest.approx(expected, rel=0.01)

    def test_project_iob_zero_confirmed(self):
        """Zero confirmed IoB should project to zero."""
        confirmed_at = datetime.now(UTC) - timedelta(hours=1)
        now = datetime.now(UTC)
        projected = project_iob(0.0, confirmed_at, now)
        assert projected == 0.0


@pytest.mark.asyncio
class TestIoBProjectionEndpoint:
    """Tests for the IoB projection API endpoint."""

    async def test_iob_projection_requires_auth(self):
        """IoB projection endpoint requires authentication."""
        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            response = await client.get("/api/integrations/tandem/iob/projection")

        assert response.status_code == 401

    async def test_iob_projection_no_data(self):
        """IoB projection returns 404 when no data available."""
        email = unique_email("iob_no_data")
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

            response = await client.get(
                "/api/integrations/tandem/iob/projection",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 404
        assert "No IoB data" in response.json()["detail"]

    async def test_iob_projection_with_data(self, db_session):
        """IoB projection returns data when pump events exist."""
        from src.core.security import hash_password
        from src.models.pump_data import PumpEvent, PumpEventType
        from src.models.user import User, UserRole

        email = unique_email("iob_with_data")
        password = "SecurePass123"

        # Create user directly in the database
        user = User(
            email=email,
            hashed_password=hash_password(password),
            role=UserRole.DIABETIC,
        )
        db_session.add(user)
        await db_session.commit()
        await db_session.refresh(user)

        # Create a pump event with IoB data
        now = datetime.now(UTC)
        pump_event = PumpEvent(
            user_id=user.id,
            event_type=PumpEventType.BOLUS,
            event_timestamp=now - timedelta(minutes=30),
            units=2.0,
            iob_at_event=2.5,
            received_at=now,
        )
        db_session.add(pump_event)
        await db_session.commit()

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            login_response = await client.post(
                "/api/auth/login",
                json={"email": email, "password": password},
            )

            session_cookie = login_response.cookies.get(settings.jwt_cookie_name)

            response = await client.get(
                "/api/integrations/tandem/iob/projection",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200

        data = response.json()
        assert data["confirmed_iob"] == 2.5
        assert "projected_iob" in data
        assert "projected_30min" in data
        assert "projected_60min" in data
        assert data["is_stale"] is False
        assert data["stale_warning"] is None

    async def test_iob_projection_stale_data(self, db_session):
        """IoB projection shows stale warning for old data."""
        from src.core.security import hash_password
        from src.models.pump_data import PumpEvent, PumpEventType
        from src.models.user import User, UserRole

        email = unique_email("iob_stale")
        password = "SecurePass123"

        # Create user directly in the database
        user = User(
            email=email,
            hashed_password=hash_password(password),
            role=UserRole.DIABETIC,
        )
        db_session.add(user)
        await db_session.commit()
        await db_session.refresh(user)

        # Create a pump event with IoB data from 3 hours ago
        now = datetime.now(UTC)
        pump_event = PumpEvent(
            user_id=user.id,
            event_type=PumpEventType.BOLUS,
            event_timestamp=now - timedelta(hours=3),
            units=2.0,
            iob_at_event=2.5,
            received_at=now - timedelta(hours=3),
        )
        db_session.add(pump_event)
        await db_session.commit()

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            login_response = await client.post(
                "/api/auth/login",
                json={"email": email, "password": password},
            )

            session_cookie = login_response.cookies.get(settings.jwt_cookie_name)

            response = await client.get(
                "/api/integrations/tandem/iob/projection",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200

        data = response.json()
        assert data["is_stale"] is True
        assert data["stale_warning"] is not None
        assert "unreliable" in data["stale_warning"].lower()

    async def test_iob_projection_decay_over_time(self, db_session):
        """IoB projection correctly applies decay curve."""
        from src.core.security import hash_password
        from src.models.pump_data import PumpEvent, PumpEventType
        from src.models.user import User, UserRole

        email = unique_email("iob_decay")
        password = "SecurePass123"

        # Create user directly in the database
        user = User(
            email=email,
            hashed_password=hash_password(password),
            role=UserRole.DIABETIC,
        )
        db_session.add(user)
        await db_session.commit()
        await db_session.refresh(user)

        # Create a pump event with IoB data from 2 hours ago
        now = datetime.now(UTC)
        pump_event = PumpEvent(
            user_id=user.id,
            event_type=PumpEventType.BOLUS,
            event_timestamp=now - timedelta(hours=2),
            units=2.0,
            iob_at_event=4.0,
            received_at=now - timedelta(hours=2),
        )
        db_session.add(pump_event)
        await db_session.commit()

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            login_response = await client.post(
                "/api/auth/login",
                json={"email": email, "password": password},
            )

            session_cookie = login_response.cookies.get(settings.jwt_cookie_name)

            response = await client.get(
                "/api/integrations/tandem/iob/projection",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200

        data = response.json()
        # After 2 hours, ~75% should remain (4.0 * 0.75 = 3.0)
        assert data["projected_iob"] == pytest.approx(3.0, rel=0.1)
        # 30 min ahead should be less
        assert data["projected_30min"] < data["projected_iob"]
        # 60 min ahead should be even less
        assert data["projected_60min"] < data["projected_30min"]
