"""Tests for the sidecar communication service (Story 15.2 / 15.4)."""

from unittest.mock import AsyncMock, MagicMock, patch

import httpx
import pytest

from src.services.sidecar import (
    get_sidecar_auth_status,
    get_sidecar_health,
    revoke_sidecar_auth,
    start_sidecar_auth,
    submit_sidecar_token,
    validate_sidecar_connection,
)


@pytest.fixture(autouse=True)
def _patch_settings(monkeypatch):
    """Set sidecar URL to a test value for all tests."""
    monkeypatch.setattr(
        "src.services.sidecar.settings.ai_sidecar_url",
        "http://test-sidecar:3456",
    )
    monkeypatch.setattr(
        "src.services.sidecar.settings.ai_sidecar_api_key",
        "test-api-key",
    )


def _make_response(json_data: dict, status_code: int = 200) -> MagicMock:
    """Create a mock httpx.Response with synchronous .json() and .raise_for_status()."""
    resp = MagicMock()
    resp.json.return_value = json_data
    resp.status_code = status_code
    resp.raise_for_status.return_value = None
    return resp


class TestGetSidecarHealth:
    @pytest.mark.asyncio
    async def test_returns_health_data(self):
        mock_resp = _make_response(
            {"status": "ok", "claude_auth": True, "codex_auth": False}
        )

        with patch("src.services.sidecar.httpx.AsyncClient") as mock_client_cls:
            mock_client = AsyncMock()
            mock_client.get.return_value = mock_resp
            mock_client.__aenter__ = AsyncMock(return_value=mock_client)
            mock_client.__aexit__ = AsyncMock(return_value=False)
            mock_client_cls.return_value = mock_client

            result = await get_sidecar_health()

        assert result is not None
        assert result["status"] == "ok"
        assert result["claude_auth"] is True

    @pytest.mark.asyncio
    async def test_returns_none_on_connection_error(self):
        with patch("src.services.sidecar.httpx.AsyncClient") as mock_client_cls:
            mock_client = AsyncMock()
            mock_client.get.side_effect = httpx.ConnectError("Connection refused")
            mock_client.__aenter__ = AsyncMock(return_value=mock_client)
            mock_client.__aexit__ = AsyncMock(return_value=False)
            mock_client_cls.return_value = mock_client

            result = await get_sidecar_health()

        assert result is None


class TestGetSidecarAuthStatus:
    @pytest.mark.asyncio
    async def test_returns_auth_status(self):
        mock_resp = _make_response(
            {"claude": {"authenticated": True}, "codex": {"authenticated": False}}
        )

        with patch("src.services.sidecar.httpx.AsyncClient") as mock_client_cls:
            mock_client = AsyncMock()
            mock_client.get.return_value = mock_resp
            mock_client.__aenter__ = AsyncMock(return_value=mock_client)
            mock_client.__aexit__ = AsyncMock(return_value=False)
            mock_client_cls.return_value = mock_client

            result = await get_sidecar_auth_status()

        assert result is not None
        assert result["claude"]["authenticated"] is True

    @pytest.mark.asyncio
    async def test_includes_auth_header(self):
        mock_resp = _make_response({"claude": {}, "codex": {}})

        with patch("src.services.sidecar.httpx.AsyncClient") as mock_client_cls:
            mock_client = AsyncMock()
            mock_client.get.return_value = mock_resp
            mock_client.__aenter__ = AsyncMock(return_value=mock_client)
            mock_client.__aexit__ = AsyncMock(return_value=False)
            mock_client_cls.return_value = mock_client

            await get_sidecar_auth_status()

            call_kwargs = mock_client.get.call_args
            headers = call_kwargs.kwargs.get("headers", {})
            assert headers["Authorization"] == "Bearer test-api-key"


class TestStartSidecarAuth:
    @pytest.mark.asyncio
    async def test_returns_auth_info(self):
        mock_resp = _make_response(
            {
                "provider": "claude",
                "auth_method": "token_paste",
                "instructions": "Run the CLI...",
            }
        )

        with patch("src.services.sidecar.httpx.AsyncClient") as mock_client_cls:
            mock_client = AsyncMock()
            mock_client.post.return_value = mock_resp
            mock_client.__aenter__ = AsyncMock(return_value=mock_client)
            mock_client.__aexit__ = AsyncMock(return_value=False)
            mock_client_cls.return_value = mock_client

            result = await start_sidecar_auth("claude")

        assert result is not None
        assert result["provider"] == "claude"
        assert result["auth_method"] == "token_paste"


class TestSubmitSidecarToken:
    @pytest.mark.asyncio
    async def test_success(self):
        mock_resp = _make_response({"success": True, "provider": "claude"})

        with patch("src.services.sidecar.httpx.AsyncClient") as mock_client_cls:
            mock_client = AsyncMock()
            mock_client.post.return_value = mock_resp
            mock_client.__aenter__ = AsyncMock(return_value=mock_client)
            mock_client.__aexit__ = AsyncMock(return_value=False)
            mock_client_cls.return_value = mock_client

            result = await submit_sidecar_token("claude", "test-token-value")

        assert result is not None
        assert result["success"] is True

    @pytest.mark.asyncio
    async def test_invalid_token_returns_error(self):
        mock_resp = _make_response({"error": "Token too short"}, status_code=400)

        with patch("src.services.sidecar.httpx.AsyncClient") as mock_client_cls:
            mock_client = AsyncMock()
            mock_client.post.return_value = mock_resp
            mock_client.__aenter__ = AsyncMock(return_value=mock_client)
            mock_client.__aexit__ = AsyncMock(return_value=False)
            mock_client_cls.return_value = mock_client

            result = await submit_sidecar_token("claude", "short")

        assert result is not None
        assert result["success"] is False
        assert "Token too short" in result["error"]

    @pytest.mark.asyncio
    async def test_non_400_error_returns_failure(self):
        mock_resp = _make_response({"error": "Unauthorized"}, status_code=401)

        with patch("src.services.sidecar.httpx.AsyncClient") as mock_client_cls:
            mock_client = AsyncMock()
            mock_client.post.return_value = mock_resp
            mock_client.__aenter__ = AsyncMock(return_value=mock_client)
            mock_client.__aexit__ = AsyncMock(return_value=False)
            mock_client_cls.return_value = mock_client

            result = await submit_sidecar_token("claude", "valid-token-value")

        assert result is not None
        assert result["success"] is False
        assert "401" in result["error"]


class TestRevokeSidecarAuth:
    @pytest.mark.asyncio
    async def test_success(self):
        mock_resp = _make_response({"revoked": True, "provider": "claude"})

        with patch("src.services.sidecar.httpx.AsyncClient") as mock_client_cls:
            mock_client = AsyncMock()
            mock_client.post.return_value = mock_resp
            mock_client.__aenter__ = AsyncMock(return_value=mock_client)
            mock_client.__aexit__ = AsyncMock(return_value=False)
            mock_client_cls.return_value = mock_client

            result = await revoke_sidecar_auth("claude")

        assert result is not None
        assert result["revoked"] is True


class TestValidateSidecarConnection:
    @pytest.mark.asyncio
    async def test_healthy_and_authenticated(self):
        health = {"status": "ok", "claude_auth": True, "codex_auth": False}
        with patch(
            "src.services.sidecar.get_sidecar_health", new_callable=AsyncMock
        ) as mock_health:
            mock_health.return_value = health
            success, error = await validate_sidecar_connection("claude")

        assert success is True
        assert error is None

    @pytest.mark.asyncio
    async def test_sidecar_unreachable(self):
        with patch(
            "src.services.sidecar.get_sidecar_health", new_callable=AsyncMock
        ) as mock_health:
            mock_health.return_value = None
            success, error = await validate_sidecar_connection("claude")

        assert success is False
        assert "not reachable" in error

    @pytest.mark.asyncio
    async def test_unhealthy_status(self):
        health = {"status": "degraded", "claude_auth": True, "codex_auth": False}
        with patch(
            "src.services.sidecar.get_sidecar_health", new_callable=AsyncMock
        ) as mock_health:
            mock_health.return_value = health
            success, error = await validate_sidecar_connection("claude")

        assert success is False
        assert "unhealthy" in error

    @pytest.mark.asyncio
    async def test_not_authenticated(self):
        health = {"status": "ok", "claude_auth": False, "codex_auth": False}
        with patch(
            "src.services.sidecar.get_sidecar_health", new_callable=AsyncMock
        ) as mock_health:
            mock_health.return_value = health
            success, error = await validate_sidecar_connection("claude")

        assert success is False
        assert "not authenticated" in error

    @pytest.mark.asyncio
    async def test_codex_provider_validation(self):
        health = {"status": "ok", "claude_auth": False, "codex_auth": True}
        with patch(
            "src.services.sidecar.get_sidecar_health", new_callable=AsyncMock
        ) as mock_health:
            mock_health.return_value = health
            success, error = await validate_sidecar_connection("codex")

        assert success is True
        assert error is None

    @pytest.mark.asyncio
    async def test_unknown_provider_rejected(self):
        success, error = await validate_sidecar_connection("gemini")

        assert success is False
        assert "Unknown sidecar provider" in error
