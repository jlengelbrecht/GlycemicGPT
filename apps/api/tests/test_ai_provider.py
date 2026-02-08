"""Story 5.1: Tests for AI provider configuration."""

import uuid
from unittest.mock import patch

from httpx import ASGITransport, AsyncClient

from src.config import settings
from src.core.encryption import decrypt_credential, encrypt_credential
from src.main import app
from src.services.ai_provider import mask_api_key


def unique_email(prefix: str = "test") -> str:
    """Generate a unique email for testing.

    Args:
        prefix: Email prefix before the UUID segment.

    Returns:
        A unique email address string.
    """
    return f"{prefix}_{uuid.uuid4().hex[:8]}@example.com"


async def register_and_login(client: AsyncClient) -> str:
    """Register a new user and return the session cookie value.

    Args:
        client: The async HTTP test client.

    Returns:
        JWT session cookie string for authenticating subsequent requests.
    """
    email = unique_email("ai_provider")
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


class TestAIProviderConfiguration:
    """Tests for AI provider configuration endpoints."""

    @patch("src.routers.ai.validate_ai_api_key")
    async def test_configure_claude_provider(self, mock_validate):
        """Test configuring Claude as AI provider with valid key."""
        mock_validate.return_value = (True, None)

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            session_cookie = await register_and_login(client)

            response = await client.post(
                "/api/ai/provider",
                json={
                    "provider_type": "claude",
                    "api_key": "sk-ant-test-valid-key-1234",
                },
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 201
        data = response.json()
        assert data["provider_type"] == "claude"
        assert data["status"] == "connected"
        assert "...1234" in data["masked_api_key"]
        assert data["last_validated_at"] is not None

    @patch("src.routers.ai.validate_ai_api_key")
    async def test_configure_openai_provider(self, mock_validate):
        """Test configuring OpenAI as AI provider with valid key."""
        mock_validate.return_value = (True, None)

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            session_cookie = await register_and_login(client)

            response = await client.post(
                "/api/ai/provider",
                json={
                    "provider_type": "openai",
                    "api_key": "sk-openai-test-valid-key-5678",
                    "model_name": "gpt-4o",
                },
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 201
        data = response.json()
        assert data["provider_type"] == "openai"
        assert data["status"] == "connected"
        assert data["model_name"] == "gpt-4o"
        assert "...5678" in data["masked_api_key"]

    @patch("src.routers.ai.validate_ai_api_key")
    async def test_reject_invalid_api_key(self, mock_validate):
        """Test that invalid API keys are rejected."""
        mock_validate.return_value = (
            False,
            "Invalid Claude API key. Please check your key and try again.",
        )

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            session_cookie = await register_and_login(client)

            response = await client.post(
                "/api/ai/provider",
                json={
                    "provider_type": "claude",
                    "api_key": "invalid-key",
                },
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 400
        data = response.json()
        assert "Invalid Claude API key" in data["detail"]

    @patch("src.routers.ai.validate_ai_api_key")
    async def test_get_provider_config(self, mock_validate):
        """Test getting current provider config returns masked key."""
        mock_validate.return_value = (True, None)

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            session_cookie = await register_and_login(client)

            # Configure provider first
            await client.post(
                "/api/ai/provider",
                json={
                    "provider_type": "claude",
                    "api_key": "sk-ant-test-key-for-get-abcd",
                },
                cookies={settings.jwt_cookie_name: session_cookie},
            )

            # Get the config
            response = await client.get(
                "/api/ai/provider",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200
        data = response.json()
        assert data["provider_type"] == "claude"
        assert data["status"] == "connected"
        # Key should be masked
        assert "abcd" in data["masked_api_key"]
        assert "sk-ant-test-key-for-get-abcd" not in data["masked_api_key"]

    async def test_get_provider_not_configured(self):
        """Test getting provider config when none configured returns 404."""
        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            session_cookie = await register_and_login(client)

            response = await client.get(
                "/api/ai/provider",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 404
        assert "No AI provider configured" in response.json()["detail"]

    @patch("src.routers.ai.validate_ai_api_key")
    async def test_delete_provider_config(self, mock_validate):
        """Test deleting AI provider configuration."""
        mock_validate.return_value = (True, None)

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            session_cookie = await register_and_login(client)

            # Configure provider first
            await client.post(
                "/api/ai/provider",
                json={
                    "provider_type": "claude",
                    "api_key": "sk-ant-test-key-delete",
                },
                cookies={settings.jwt_cookie_name: session_cookie},
            )

            # Delete it
            response = await client.delete(
                "/api/ai/provider",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200
        assert "removed successfully" in response.json()["message"]

    async def test_delete_provider_not_configured(self):
        """Test deleting when no provider configured returns 404."""
        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            session_cookie = await register_and_login(client)

            response = await client.delete(
                "/api/ai/provider",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 404

    @patch("src.routers.ai.validate_ai_api_key")
    async def test_update_existing_provider(self, mock_validate):
        """Test updating an existing provider configuration."""
        mock_validate.return_value = (True, None)

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            session_cookie = await register_and_login(client)

            # Configure Claude first
            await client.post(
                "/api/ai/provider",
                json={
                    "provider_type": "claude",
                    "api_key": "sk-ant-original-key-1111",
                },
                cookies={settings.jwt_cookie_name: session_cookie},
            )

            # Update to OpenAI
            response = await client.post(
                "/api/ai/provider",
                json={
                    "provider_type": "openai",
                    "api_key": "sk-openai-new-key-2222",
                    "model_name": "gpt-4o",
                },
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 201
        data = response.json()
        assert data["provider_type"] == "openai"
        assert data["model_name"] == "gpt-4o"
        assert "...2222" in data["masked_api_key"]

    async def test_configure_requires_auth(self):
        """Test that all AI provider endpoints require authentication."""
        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            # POST
            response = await client.post(
                "/api/ai/provider",
                json={
                    "provider_type": "claude",
                    "api_key": "sk-test",
                },
            )
            assert response.status_code == 401

            # GET
            response = await client.get("/api/ai/provider")
            assert response.status_code == 401

            # DELETE
            response = await client.delete("/api/ai/provider")
            assert response.status_code == 401

            # POST test
            response = await client.post("/api/ai/provider/test")
            assert response.status_code == 401

    @patch("src.routers.ai.validate_ai_api_key")
    async def test_test_endpoint_success(self, mock_validate):
        """Test the /provider/test endpoint when key is valid."""
        mock_validate.return_value = (True, None)

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            session_cookie = await register_and_login(client)

            # Configure provider first
            await client.post(
                "/api/ai/provider",
                json={
                    "provider_type": "claude",
                    "api_key": "sk-ant-test-success-9999",
                },
                cookies={settings.jwt_cookie_name: session_cookie},
            )

            # Test the stored key
            response = await client.post(
                "/api/ai/provider/test",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200
        data = response.json()
        assert data["success"] is True
        assert "successfully" in data["message"]

    @patch("src.routers.ai.validate_ai_api_key")
    async def test_test_endpoint_failure(self, mock_validate):
        """Test the /provider/test endpoint when key validation fails."""
        # validate_ai_api_key is called twice: once during POST /provider (succeeds)
        # and once during POST /provider/test (fails), simulating a revoked key.
        mock_validate.side_effect = [
            (True, None),
            (False, "Invalid Claude API key. Please check your key and try again."),
        ]

        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            session_cookie = await register_and_login(client)

            # Configure provider first (succeeds)
            await client.post(
                "/api/ai/provider",
                json={
                    "provider_type": "claude",
                    "api_key": "sk-ant-test-will-fail-0000",
                },
                cookies={settings.jwt_cookie_name: session_cookie},
            )

            # Test the stored key (fails)
            response = await client.post(
                "/api/ai/provider/test",
                cookies={settings.jwt_cookie_name: session_cookie},
            )

        assert response.status_code == 200
        data = response.json()
        assert data["success"] is False
        assert "Invalid Claude API key" in data["message"]


class TestAPIKeyEncryption:
    """Tests for API key encryption roundtrip."""

    def test_encrypt_decrypt_roundtrip(self):
        """Test that encrypting then decrypting returns the original key."""
        api_key = "sk-ant-test-key-roundtrip-12345"
        encrypted = encrypt_credential(api_key)
        decrypted = decrypt_credential(encrypted)
        assert decrypted == api_key

    def test_encryption_produces_different_ciphertext(self):
        """Test that the same key produces different ciphertext each time (Fernet uses timestamp)."""
        api_key = "sk-ant-test-key-different"
        encrypted1 = encrypt_credential(api_key)
        encrypted2 = encrypt_credential(api_key)
        assert encrypted1 != encrypted2
        # But both decrypt to the same value
        assert decrypt_credential(encrypted1) == api_key
        assert decrypt_credential(encrypted2) == api_key


class TestMaskAPIKey:
    """Tests for API key masking utility."""

    def test_mask_standard_key(self):
        """Test masking a standard API key."""
        result = mask_api_key("sk-ant-test-key-abcd")
        assert result == "sk-...abcd"

    def test_mask_openai_key(self):
        """Test masking an OpenAI-style key."""
        result = mask_api_key("sk-proj-xyz123")
        assert result == "sk-...z123"

    def test_mask_short_key(self):
        """Test masking a very short key."""
        result = mask_api_key("ab")
        assert result == "****"

    def test_mask_non_sk_prefix(self):
        """Test masking a key without sk- prefix."""
        result = mask_api_key("xai-test-key-9876")
        assert result == "...9876"
