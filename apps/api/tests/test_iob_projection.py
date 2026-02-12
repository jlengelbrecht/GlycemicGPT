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
    _sum_iob_from_doses,
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


class TestSumIoBFromDoses:
    """Tests for the dose-summation pure function."""

    def test_single_recent_bolus(self):
        """A bolus delivered 1 hour ago should retain ~94% of its insulin."""
        now = datetime.now(UTC)
        doses = [(now - timedelta(hours=1), 2.0)]
        iob = _sum_iob_from_doses(doses, now)
        # 2.0 * 0.9375 = 1.875
        assert iob == pytest.approx(1.875, rel=0.01)

    def test_multiple_boluses(self):
        """Multiple boluses within DIA window should sum their remaining insulin."""
        now = datetime.now(UTC)
        doses = [
            (now - timedelta(hours=1), 2.0),  # 2.0 * 0.9375 = 1.875
            (now - timedelta(hours=2), 3.0),  # 3.0 * 0.75 = 2.25
        ]
        iob = _sum_iob_from_doses(doses, now)
        assert iob == pytest.approx(1.875 + 2.25, rel=0.01)

    def test_dose_outside_dia_contributes_zero(self):
        """A dose delivered beyond DIA hours ago should contribute 0."""
        now = datetime.now(UTC)
        doses = [(now - timedelta(hours=5), 5.0)]  # beyond 4h DIA
        iob = _sum_iob_from_doses(doses, now)
        assert iob == 0.0

    def test_no_doses_returns_zero(self):
        """With no doses, IoB should be 0."""
        now = datetime.now(UTC)
        iob = _sum_iob_from_doses([], now)
        assert iob == 0.0

    def test_future_dose_ignored(self):
        """A dose in the future relative to at_time should not contribute."""
        now = datetime.now(UTC)
        doses = [(now + timedelta(hours=1), 5.0)]
        iob = _sum_iob_from_doses(doses, now)
        assert iob == 0.0

    def test_dose_just_delivered(self):
        """A dose at exactly at_time should retain 100%."""
        now = datetime.now(UTC)
        doses = [(now, 3.0)]
        iob = _sum_iob_from_doses(doses, now)
        assert iob == pytest.approx(3.0, rel=0.01)


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
        assert data["is_estimated"] is False

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

    async def test_iob_includes_post_confirmation_bolus(self, db_session):
        """Boluses delivered after the pump's IoB snapshot increase IoB."""
        from src.core.security import hash_password
        from src.models.pump_data import PumpEvent, PumpEventType
        from src.models.user import User, UserRole

        email = unique_email("iob_hybrid")
        password = "SecurePass123"

        user = User(
            email=email,
            hashed_password=hash_password(password),
            role=UserRole.DIABETIC,
        )
        db_session.add(user)
        await db_session.commit()
        await db_session.refresh(user)

        now = datetime.now(UTC)

        # Pump-confirmed IoB snapshot: 2.0u at 1 hour ago
        snapshot_event = PumpEvent(
            user_id=user.id,
            event_type=PumpEventType.CORRECTION,
            event_timestamp=now - timedelta(hours=1),
            units=0.5,
            iob_at_event=2.0,
            received_at=now - timedelta(hours=1),
        )
        # A 3.0u bolus delivered 30 minutes AFTER the snapshot
        post_bolus = PumpEvent(
            user_id=user.id,
            event_type=PumpEventType.BOLUS,
            event_timestamp=now - timedelta(minutes=30),
            units=3.0,
            iob_at_event=None,
            received_at=now - timedelta(minutes=30),
        )
        db_session.add_all([snapshot_event, post_bolus])
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

        # pump_component: 2.0 * remaining(1h, 4h) = 2.0 * 0.9375 = 1.875
        # post_component: 3.0 * remaining(0.5h, 4h) = 3.0 * 0.984375 = 2.953
        # total ≈ 4.83
        # Without the fix, this would just be 1.875 (ignoring the 3.0u bolus)
        assert data["projected_iob"] > 4.0
        assert data["projected_iob"] == pytest.approx(4.83, rel=0.1)
        assert data["confirmed_iob"] == 2.0

    async def test_iob_no_double_counting(self, db_session):
        """Doses before the pump confirmation are NOT double-counted."""
        from src.core.security import hash_password
        from src.models.pump_data import PumpEvent, PumpEventType
        from src.models.user import User, UserRole

        email = unique_email("iob_nodup")
        password = "SecurePass123"

        user = User(
            email=email,
            hashed_password=hash_password(password),
            role=UserRole.DIABETIC,
        )
        db_session.add(user)
        await db_session.commit()
        await db_session.refresh(user)

        now = datetime.now(UTC)

        # A bolus from 2 hours ago (units=5.0)
        old_bolus = PumpEvent(
            user_id=user.id,
            event_type=PumpEventType.BOLUS,
            event_timestamp=now - timedelta(hours=2),
            units=5.0,
            iob_at_event=None,
            received_at=now - timedelta(hours=2),
        )
        # Pump snapshot 1 hour ago already includes the old bolus's IoB
        snapshot = PumpEvent(
            user_id=user.id,
            event_type=PumpEventType.CORRECTION,
            event_timestamp=now - timedelta(hours=1),
            units=0.5,
            iob_at_event=3.0,  # pump's total IoB at that point
            received_at=now - timedelta(hours=1),
        )
        db_session.add_all([old_bolus, snapshot])
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

        # pump_component: 3.0 * remaining(1h, 4h) = 3.0 * 0.9375 = 2.8125
        # post_component: 0 (old_bolus is BEFORE snapshot, so excluded)
        # total ≈ 2.81
        # If double-counted, it would be 2.81 + 5.0*0.75 = 6.56 (wrong!)
        assert data["projected_iob"] == pytest.approx(2.81, rel=0.1)

    async def test_iob_dose_only_fallback(self, db_session):
        """When no pump confirmation exists, uses pure dose summation."""
        from src.core.security import hash_password
        from src.models.pump_data import PumpEvent, PumpEventType
        from src.models.user import User, UserRole

        email = unique_email("iob_fallback")
        password = "SecurePass123"

        user = User(
            email=email,
            hashed_password=hash_password(password),
            role=UserRole.DIABETIC,
        )
        db_session.add(user)
        await db_session.commit()
        await db_session.refresh(user)

        now = datetime.now(UTC)

        # Bolus with no pump IoB snapshot
        bolus = PumpEvent(
            user_id=user.id,
            event_type=PumpEventType.BOLUS,
            event_timestamp=now - timedelta(hours=1),
            units=4.0,
            iob_at_event=None,
            received_at=now - timedelta(hours=1),
        )
        db_session.add(bolus)
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

        # Pure dose sum: 4.0 * remaining(1h, 4h) = 4.0 * 0.9375 = 3.75
        assert data["projected_iob"] == pytest.approx(3.75, rel=0.1)
        assert data["is_estimated"] is True
        assert "estimated" in data["stale_warning"].lower()

    async def test_iob_same_timestamp_not_double_counted(self, db_session):
        """A dose at the exact same timestamp as the snapshot is not double-counted."""
        from src.core.security import hash_password
        from src.models.pump_data import PumpEvent, PumpEventType
        from src.models.user import User, UserRole

        email = unique_email("iob_same_ts")
        password = "SecurePass123"

        user = User(
            email=email,
            hashed_password=hash_password(password),
            role=UserRole.DIABETIC,
        )
        db_session.add(user)
        await db_session.commit()
        await db_session.refresh(user)

        now = datetime.now(UTC)
        snapshot_time = now - timedelta(hours=1)

        # Bolus and IoB snapshot at the exact same timestamp
        event = PumpEvent(
            user_id=user.id,
            event_type=PumpEventType.BOLUS,
            event_timestamp=snapshot_time,
            units=5.0,
            iob_at_event=3.0,  # pump's IoB already includes this bolus
            received_at=snapshot_time,
        )
        db_session.add(event)
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

        # Should only be the decayed snapshot (3.0 * 0.9375 = 2.81)
        # NOT snapshot + dose (which would be 2.81 + 5.0*0.9375 = 7.5)
        assert data["projected_iob"] == pytest.approx(2.81, rel=0.1)

    async def test_iob_fetch_excludes_non_bolus_events(self, db_session):
        """Basal and other non-bolus events are excluded from dose summation."""
        from src.core.security import hash_password
        from src.models.pump_data import PumpEvent, PumpEventType
        from src.models.user import User, UserRole

        email = unique_email("iob_excl")
        password = "SecurePass123"

        user = User(
            email=email,
            hashed_password=hash_password(password),
            role=UserRole.DIABETIC,
        )
        db_session.add(user)
        await db_session.commit()
        await db_session.refresh(user)

        now = datetime.now(UTC)

        # Pump snapshot 2 hours ago
        snapshot = PumpEvent(
            user_id=user.id,
            event_type=PumpEventType.CORRECTION,
            event_timestamp=now - timedelta(hours=2),
            units=0.5,
            iob_at_event=1.0,
            received_at=now - timedelta(hours=2),
        )
        # Basal event after snapshot (should NOT be added to IoB)
        basal = PumpEvent(
            user_id=user.id,
            event_type=PumpEventType.BASAL,
            event_timestamp=now - timedelta(hours=1),
            units=0.8,
            iob_at_event=None,
            received_at=now - timedelta(hours=1),
        )
        # Suspend event after snapshot (should NOT be added)
        suspend = PumpEvent(
            user_id=user.id,
            event_type=PumpEventType.SUSPEND,
            event_timestamp=now - timedelta(minutes=30),
            units=None,
            iob_at_event=None,
            received_at=now - timedelta(minutes=30),
        )
        db_session.add_all([snapshot, basal, suspend])
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

        # Should only be decayed snapshot: 1.0 * remaining(2h, 4h) = 1.0 * 0.75 = 0.75
        # Basal and suspend events should NOT contribute
        assert data["projected_iob"] == pytest.approx(0.75, rel=0.1)
        assert data["is_estimated"] is False
