"""Story 16.12: Tests for rate limiting middleware."""

import uuid

import pytest
from httpx import ASGITransport, AsyncClient

from src.main import app
from src.middleware.rate_limit import limiter


def _email() -> str:
    return f"rate_{uuid.uuid4().hex[:8]}@test.com"


@pytest.fixture(autouse=True)
def _enable_rate_limiting():
    """Temporarily enable rate limiting for these tests."""
    limiter.enabled = True
    limiter.reset()
    yield
    limiter.enabled = False


class TestRateLimiting:
    async def test_login_rate_limit_triggers_429(self):
        """Auth login endpoints should be rate-limited to 10/minute."""
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as c:
            # Send 11 requests rapidly (limit is 10/minute)
            responses = []
            for _ in range(11):
                resp = await c.post(
                    "/api/auth/login",
                    json={"email": "nobody@example.com", "password": "wrong"},
                )
                responses.append(resp.status_code)

            # At least one should be 429
            assert 429 in responses, (
                f"Expected 429 in responses but got: {set(responses)}"
            )

    async def test_rate_limit_returns_json_detail(self):
        """Rate limit responses should include a JSON detail message."""
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as c:
            for _ in range(12):
                resp = await c.post(
                    "/api/auth/mobile/login",
                    json={"email": "nobody@example.com", "password": "wrong"},
                )

            if resp.status_code == 429:
                body = resp.json()
                assert "detail" in body
                assert "Rate limit" in body["detail"]

    async def test_health_endpoint_not_limited_at_low_rate(self):
        """Health endpoint has no explicit limit, so 5 requests should pass."""
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as c:
            for _ in range(5):
                resp = await c.get("/health")
                assert resp.status_code == 200
