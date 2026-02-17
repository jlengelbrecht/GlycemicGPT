"""Story 16.6: Tests for Tandem cloud upload service."""

import base64
import hashlib
import hmac
import json
import uuid
from datetime import UTC, datetime, timedelta
from unittest.mock import MagicMock, patch

import pytest
from httpx import ASGITransport, AsyncClient

from src.main import app
from src.services.tandem_upload import (
    _authenticate_fresh,
    build_upload_payload,
    sign_tdc_token,
)


def _email() -> str:
    return f"upload_{uuid.uuid4().hex[:8]}@test.com"


async def _register_and_mobile_login(
    client: AsyncClient, email: str, password: str = "TestPass1"
) -> str:
    await client.post(
        "/api/auth/register",
        json={"email": email, "password": password},
    )
    resp = await client.post(
        "/api/auth/mobile/login",
        json={"email": email, "password": password},
    )
    assert resp.status_code == 200
    return resp.json()["access_token"]


_TEST_HMAC_KEY = b"test-hmac-key-for-unit-tests"


class TestHMACSigning:
    """Test HMAC-SHA1 signing matches the Tandem protocol."""

    def test_sign_tdc_token_produces_valid_hmac(self):
        body = b'{"client":"mHealth","package":{"device":{}}}'
        result = sign_tdc_token(body, hmac_key=_TEST_HMAC_KEY)
        # Verify it's valid base64
        decoded = base64.b64decode(result)
        assert len(decoded) == 20  # SHA1 produces 20 bytes

    def test_sign_tdc_token_is_deterministic(self):
        body = b'{"test":"data"}'
        assert sign_tdc_token(body, hmac_key=_TEST_HMAC_KEY) == sign_tdc_token(
            body, hmac_key=_TEST_HMAC_KEY
        )

    def test_sign_tdc_token_matches_manual_computation(self):
        body = b'{"foo":"bar"}'
        expected = base64.b64encode(
            hmac.new(_TEST_HMAC_KEY, body, hashlib.sha1).digest()
        ).decode("ascii")
        assert sign_tdc_token(body, hmac_key=_TEST_HMAC_KEY) == expected

    def test_different_bodies_produce_different_signatures(self):
        assert sign_tdc_token(b"body1", hmac_key=_TEST_HMAC_KEY) != sign_tdc_token(
            b"body2", hmac_key=_TEST_HMAC_KEY
        )


class TestBuildUploadPayload:
    """Test payload construction matches the official app schema."""

    def _make_mock_pump_info(self):
        """Create a mock pump hardware info object."""

        class MockPumpInfo:
            serial_number = 12345678
            model_number = 99
            part_number = 11111
            pump_rev = "3.0"
            arm_sw_ver = 50000
            msp_sw_ver = 50000
            config_a_bits = 0
            config_b_bits = 0
            pcba_sn = 99999
            pcba_rev = "A"
            pump_features = {
                "dexcomG5": False,
                "basalIQ": False,
                "dexcomG6": True,
                "controlIQ": True,
                "dexcomG7": True,
                "abbottFsl2": False,
            }

        return MockPumpInfo()

    def _make_mock_raw_event(self, seq=1):
        """Create a mock raw event."""

        class MockRawEvent:
            sequence_number = seq
            raw_bytes_b64 = base64.b64encode(b"test_event_bytes").decode()
            event_type_id = 280
            pump_time_seconds = 1000000

        return MockRawEvent()

    def test_top_level_structure(self):
        pump_info = self._make_mock_pump_info()
        payload = build_upload_payload(pump_info, [])
        assert payload["client"] == "mHealth"
        assert "package" in payload
        assert "device" in payload["package"]

    def test_device_fields(self):
        pump_info = self._make_mock_pump_info()
        payload = build_upload_payload(pump_info, [])
        device = payload["package"]["device"]
        assert device["serialNum"] == 12345678
        assert device["modelNum"] == 99
        assert device["pumpRev"] == "3.0"
        assert "data" in device

    def test_misc_section(self):
        pump_info = self._make_mock_pump_info()
        payload = build_upload_payload(pump_info, [])
        misc = payload["package"]["device"]["data"]["misc"]
        assert misc["uploaderClient"] == "mobile_tconnect"
        assert misc["appVersion"] == "2.9.1 (3368rb)"
        assert "pumpFeatures" in misc

    def test_events_included(self):
        pump_info = self._make_mock_pump_info()
        events = [self._make_mock_raw_event(i) for i in range(3)]
        payload = build_upload_payload(pump_info, events)
        data = payload["package"]["device"]["data"]
        assert "events" in data
        assert len(data["events"]) == 3
        assert all(isinstance(e, str) for e in data["events"])

    def test_no_events_omits_key(self):
        pump_info = self._make_mock_pump_info()
        payload = build_upload_payload(pump_info, [])
        data = payload["package"]["device"]["data"]
        assert "events" not in data

    def test_settings_included_when_provided(self):
        pump_info = self._make_mock_pump_info()
        payload = build_upload_payload(pump_info, [], settings_b64="abc123")
        data = payload["package"]["device"]["data"]
        assert data["settings"] == "abc123"

    def test_device_assignment_id_included(self):
        pump_info = self._make_mock_pump_info()
        payload = build_upload_payload(
            pump_info, [], device_assignment_id="abc-123-pump"
        )
        misc = payload["package"]["device"]["data"]["misc"]
        assert misc["deviceAssignmentId"] == "abc-123-pump"

    def test_device_assignment_id_defaults_to_empty(self):
        pump_info = self._make_mock_pump_info()
        payload = build_upload_payload(pump_info, [])
        misc = payload["package"]["device"]["data"]["misc"]
        assert misc["deviceAssignmentId"] == ""

    def test_payload_is_json_serializable(self):
        pump_info = self._make_mock_pump_info()
        events = [self._make_mock_raw_event(i) for i in range(5)]
        payload = build_upload_payload(pump_info, events)
        # Should not raise
        json_str = json.dumps(payload)
        assert len(json_str) > 0


class TestPumpPushWithRawEvents:
    """Test the extended pump push endpoint with raw events and hardware info."""

    async def test_push_with_raw_events(self):
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as c:
            token = await _register_and_mobile_login(c, _email())
            now = datetime.now(UTC).isoformat()
            resp = await c.post(
                "/api/integrations/pump/push",
                json={
                    "events": [
                        {
                            "event_type": "bolus",
                            "event_timestamp": now,
                            "units": 2.5,
                        }
                    ],
                    "raw_events": [
                        {
                            "sequence_number": 100,
                            "raw_bytes_b64": base64.b64encode(b"test").decode(),
                            "event_type_id": 280,
                            "pump_time_seconds": 1000000,
                        }
                    ],
                    "source": "mobile",
                },
                headers={"Authorization": f"Bearer {token}"},
            )
        assert resp.status_code == 200
        body = resp.json()
        assert body["accepted"] == 1
        assert body["raw_accepted"] == 1

    async def test_push_with_hardware_info(self):
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as c:
            token = await _register_and_mobile_login(c, _email())
            now = datetime.now(UTC).isoformat()
            resp = await c.post(
                "/api/integrations/pump/push",
                json={
                    "events": [
                        {
                            "event_type": "basal",
                            "event_timestamp": now,
                            "units": 0.8,
                        }
                    ],
                    "pump_info": {
                        "serial_number": 12345678,
                        "model_number": 99,
                        "part_number": 11111,
                        "pump_rev": "3.0",
                        "arm_sw_ver": 50000,
                        "msp_sw_ver": 50000,
                        "config_a_bits": 0,
                        "config_b_bits": 0,
                        "pcba_sn": 99999,
                        "pcba_rev": "A",
                        "pump_features": {"controlIQ": True},
                    },
                    "source": "mobile",
                },
                headers={"Authorization": f"Bearer {token}"},
            )
        assert resp.status_code == 200
        body = resp.json()
        assert body["accepted"] == 1

    async def test_push_without_raw_events_backward_compatible(self):
        """Existing push requests without raw_events/pump_info still work."""
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as c:
            token = await _register_and_mobile_login(c, _email())
            now = datetime.now(UTC).isoformat()
            resp = await c.post(
                "/api/integrations/pump/push",
                json={
                    "events": [
                        {
                            "event_type": "bolus",
                            "event_timestamp": now,
                            "units": 3.0,
                        }
                    ],
                },
                headers={"Authorization": f"Bearer {token}"},
            )
        assert resp.status_code == 200
        body = resp.json()
        assert body["accepted"] == 1
        assert body["raw_accepted"] == 0
        assert body["raw_duplicates"] == 0

    async def test_raw_event_deduplication(self):
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as c:
            token = await _register_and_mobile_login(c, _email())
            now = datetime.now(UTC).isoformat()
            raw_event = {
                "sequence_number": 200,
                "raw_bytes_b64": base64.b64encode(b"data").decode(),
                "event_type_id": 279,
                "pump_time_seconds": 2000000,
            }
            # First push
            resp1 = await c.post(
                "/api/integrations/pump/push",
                json={
                    "events": [
                        {
                            "event_type": "basal",
                            "event_timestamp": now,
                            "units": 0.5,
                        }
                    ],
                    "raw_events": [raw_event],
                },
                headers={"Authorization": f"Bearer {token}"},
            )
            assert resp1.status_code == 200
            assert resp1.json()["raw_accepted"] == 1

            # Second push with same sequence number
            now2 = (datetime.now(UTC) + timedelta(seconds=1)).isoformat()
            resp2 = await c.post(
                "/api/integrations/pump/push",
                json={
                    "events": [
                        {
                            "event_type": "bolus",
                            "event_timestamp": now2,
                            "units": 1.0,
                        }
                    ],
                    "raw_events": [raw_event],
                },
                headers={"Authorization": f"Bearer {token}"},
            )
            assert resp2.status_code == 200
            assert resp2.json()["raw_duplicates"] == 1


class TestTandemUploadStatusEndpoints:
    """Test the Tandem cloud upload status/settings endpoints."""

    async def test_get_status_default(self):
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as c:
            token = await _register_and_mobile_login(c, _email())
            resp = await c.get(
                "/api/integrations/tandem/cloud-upload/status",
                headers={"Authorization": f"Bearer {token}"},
            )
        assert resp.status_code == 200
        body = resp.json()
        assert body["enabled"] is False
        assert body["upload_interval_minutes"] == 15

    async def test_update_settings(self):
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as c:
            token = await _register_and_mobile_login(c, _email())
            resp = await c.put(
                "/api/integrations/tandem/cloud-upload/settings",
                json={"enabled": True, "interval_minutes": 10},
                headers={"Authorization": f"Bearer {token}"},
            )
        assert resp.status_code == 200
        body = resp.json()
        assert body["enabled"] is True
        assert body["upload_interval_minutes"] == 10

    async def test_update_settings_invalid_interval(self):
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as c:
            token = await _register_and_mobile_login(c, _email())
            resp = await c.put(
                "/api/integrations/tandem/cloud-upload/settings",
                json={"enabled": True, "interval_minutes": 7},
                headers={"Authorization": f"Bearer {token}"},
            )
        assert resp.status_code == 422  # Validation error


class TestAuthenticateFresh:
    """Test _authenticate_fresh extracts correct attributes from TandemSourceApi."""

    @pytest.mark.asyncio
    async def test_extracts_access_token_camelcase(self):
        """Verify we read api.accessToken (camelCase), not _access_token."""
        mock_api = MagicMock()
        mock_api.accessToken = "test-token-12345"
        mock_api.accessTokenExpiresAt = None
        mock_api.pumperId = "pump-123"

        with patch(
            "tconnectsync.api.tandemsource.TandemSourceApi",
            return_value=mock_api,
        ):
            result = await _authenticate_fresh("user@test.com", "pass", "US")

        assert result["access_token"] == "test-token-12345"
        assert result["pumper_id"] == "pump-123"
        assert result["refresh_token"] is None

    @pytest.mark.asyncio
    async def test_raises_if_no_access_token(self):
        """Verify clear error if accessToken is missing/None."""
        mock_api = MagicMock()
        mock_api.accessToken = None
        mock_api.accessTokenExpiresAt = None
        mock_api.pumperId = None

        with (
            patch(
                "tconnectsync.api.tandemsource.TandemSourceApi",
                return_value=mock_api,
            ),
            pytest.raises(RuntimeError, match="accessToken"),
        ):
            await _authenticate_fresh("user@test.com", "pass", "US")

    @pytest.mark.asyncio
    async def test_defaults_expires_in_when_no_expiry(self):
        """Verify fallback to 3600 when accessTokenExpiresAt is None."""
        mock_api = MagicMock()
        mock_api.accessToken = "tok"
        mock_api.accessTokenExpiresAt = None
        mock_api.pumperId = None

        with patch(
            "tconnectsync.api.tandemsource.TandemSourceApi",
            return_value=mock_api,
        ):
            result = await _authenticate_fresh("user@test.com", "pass", "US")

        assert result["expires_in"] == 3600

    @pytest.mark.asyncio
    async def test_computes_real_expires_in(self):
        """Verify computed TTL from arrow datetime."""
        import arrow

        future = arrow.get().shift(minutes=+30)
        mock_api = MagicMock()
        mock_api.accessToken = "tok"
        mock_api.accessTokenExpiresAt = future
        mock_api.pumperId = None

        with patch(
            "tconnectsync.api.tandemsource.TandemSourceApi",
            return_value=mock_api,
        ):
            result = await _authenticate_fresh("user@test.com", "pass", "US")

        # Should be approximately 1800 seconds (30 minutes), allow some slack
        assert 1750 < result["expires_in"] < 1850
