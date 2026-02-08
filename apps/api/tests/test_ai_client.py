"""Story 5.2: Tests for BYOAI abstraction layer."""

import uuid
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock, patch

import anthropic
import openai
import pytest

from src.integrations.claude import ClaudeClient
from src.integrations.openai_client import OpenAIClient
from src.models.ai_provider import AIProviderType
from src.schemas.ai_response import AIMessage, AIResponse


def _user_message(content: str = "Hello") -> list[AIMessage]:
    """Create a single user message list for testing."""
    return [AIMessage(role="user", content=content)]


class TestClaudeClient:
    """Tests for ClaudeClient.generate()."""

    @patch("src.integrations.claude.anthropic.AsyncAnthropic")
    async def test_generate_returns_normalized_response(self, mock_cls):
        """Test that Claude generate() returns a normalised AIResponse."""
        mock_client = AsyncMock()
        mock_cls.return_value = mock_client
        mock_client.messages.create.return_value = SimpleNamespace(
            content=[SimpleNamespace(text="Hello from Claude")],
            model="claude-sonnet-4-5-20250929",
            usage=SimpleNamespace(input_tokens=10, output_tokens=5),
        )

        client = ClaudeClient(api_key="sk-test", model="claude-sonnet-4-5-20250929")
        result = await client.generate(_user_message())

        assert isinstance(result, AIResponse)
        assert result.content == "Hello from Claude"
        assert result.model == "claude-sonnet-4-5-20250929"
        assert result.provider == AIProviderType.CLAUDE
        assert result.usage.input_tokens == 10
        assert result.usage.output_tokens == 5

    @patch("src.integrations.claude.anthropic.AsyncAnthropic")
    async def test_generate_with_system_prompt(self, mock_cls):
        """Test that system prompt is passed to the Anthropic API."""
        mock_client = AsyncMock()
        mock_cls.return_value = mock_client
        mock_client.messages.create.return_value = SimpleNamespace(
            content=[SimpleNamespace(text="response")],
            model="claude-sonnet-4-5-20250929",
            usage=SimpleNamespace(input_tokens=20, output_tokens=10),
        )

        client = ClaudeClient(api_key="sk-test", model="claude-sonnet-4-5-20250929")
        await client.generate(
            _user_message(),
            system_prompt="You are a diabetes assistant.",
        )

        call_kwargs = mock_client.messages.create.call_args[1]
        assert call_kwargs["system"] == "You are a diabetes assistant."

    @patch("src.integrations.claude.anthropic.AsyncAnthropic")
    async def test_generate_without_system_prompt(self, mock_cls):
        """Test that system key is omitted when no system prompt given."""
        mock_client = AsyncMock()
        mock_cls.return_value = mock_client
        mock_client.messages.create.return_value = SimpleNamespace(
            content=[SimpleNamespace(text="response")],
            model="claude-sonnet-4-5-20250929",
            usage=SimpleNamespace(input_tokens=5, output_tokens=3),
        )

        client = ClaudeClient(api_key="sk-test", model="claude-sonnet-4-5-20250929")
        await client.generate(_user_message())

        call_kwargs = mock_client.messages.create.call_args[1]
        assert "system" not in call_kwargs

    @patch("src.integrations.claude.anthropic.AsyncAnthropic")
    async def test_generate_auth_error_raises(self, mock_cls):
        """Test that authentication errors propagate."""
        mock_client = AsyncMock()
        mock_cls.return_value = mock_client
        mock_client.messages.create.side_effect = anthropic.AuthenticationError(
            message="invalid key",
            response=MagicMock(status_code=401),
            body=None,
        )

        client = ClaudeClient(api_key="sk-bad", model="claude-sonnet-4-5-20250929")
        with pytest.raises(anthropic.AuthenticationError):
            await client.generate(_user_message())

    @patch("src.integrations.claude.anthropic.AsyncAnthropic")
    async def test_generate_rate_limit_raises(self, mock_cls):
        """Test that rate limit errors propagate."""
        mock_client = AsyncMock()
        mock_cls.return_value = mock_client
        mock_client.messages.create.side_effect = anthropic.RateLimitError(
            message="rate limited",
            response=MagicMock(status_code=429),
            body=None,
        )

        client = ClaudeClient(api_key="sk-test", model="claude-sonnet-4-5-20250929")
        with pytest.raises(anthropic.RateLimitError):
            await client.generate(_user_message())

    @patch("src.integrations.claude.anthropic.AsyncAnthropic")
    async def test_generate_connection_error_raises(self, mock_cls):
        """Test that connection errors propagate."""
        mock_client = AsyncMock()
        mock_cls.return_value = mock_client
        mock_client.messages.create.side_effect = anthropic.APIConnectionError(
            request=MagicMock(),
        )

        client = ClaudeClient(api_key="sk-test", model="claude-sonnet-4-5-20250929")
        with pytest.raises(anthropic.APIConnectionError):
            await client.generate(_user_message())

    @patch("src.integrations.claude.anthropic.AsyncAnthropic")
    async def test_generate_unexpected_error_raises(self, mock_cls):
        """Test that unexpected errors propagate."""
        mock_client = AsyncMock()
        mock_cls.return_value = mock_client
        mock_client.messages.create.side_effect = RuntimeError("unexpected")

        client = ClaudeClient(api_key="sk-test", model="claude-sonnet-4-5-20250929")
        with pytest.raises(RuntimeError, match="unexpected"):
            await client.generate(_user_message())

    @patch("src.integrations.claude.anthropic.AsyncAnthropic")
    async def test_generate_empty_content_returns_empty_string(self, mock_cls):
        """Test that empty content list returns empty string."""
        mock_client = AsyncMock()
        mock_cls.return_value = mock_client
        mock_client.messages.create.return_value = SimpleNamespace(
            content=[],
            model="claude-sonnet-4-5-20250929",
            usage=SimpleNamespace(input_tokens=5, output_tokens=0),
        )

        client = ClaudeClient(api_key="sk-test", model="claude-sonnet-4-5-20250929")
        result = await client.generate(_user_message())

        assert result.content == ""


class TestOpenAIClient:
    """Tests for OpenAIClient.generate()."""

    @patch("src.integrations.openai_client.openai.AsyncOpenAI")
    async def test_generate_returns_normalized_response(self, mock_cls):
        """Test that OpenAI generate() returns a normalised AIResponse."""
        mock_client = AsyncMock()
        mock_cls.return_value = mock_client
        mock_client.chat.completions.create.return_value = SimpleNamespace(
            choices=[
                SimpleNamespace(message=SimpleNamespace(content="Hello from OpenAI"))
            ],
            model="gpt-4o",
            usage=SimpleNamespace(prompt_tokens=8, completion_tokens=4),
        )

        client = OpenAIClient(api_key="sk-test", model="gpt-4o")
        result = await client.generate(_user_message())

        assert isinstance(result, AIResponse)
        assert result.content == "Hello from OpenAI"
        assert result.model == "gpt-4o"
        assert result.provider == AIProviderType.OPENAI
        assert result.usage.input_tokens == 8
        assert result.usage.output_tokens == 4

    @patch("src.integrations.openai_client.openai.AsyncOpenAI")
    async def test_generate_with_system_prompt(self, mock_cls):
        """Test that system prompt is prepended as system message for OpenAI."""
        mock_client = AsyncMock()
        mock_cls.return_value = mock_client
        mock_client.chat.completions.create.return_value = SimpleNamespace(
            choices=[SimpleNamespace(message=SimpleNamespace(content="response"))],
            model="gpt-4o",
            usage=SimpleNamespace(prompt_tokens=15, completion_tokens=5),
        )

        client = OpenAIClient(api_key="sk-test", model="gpt-4o")
        await client.generate(
            _user_message(),
            system_prompt="You are a diabetes assistant.",
        )

        call_kwargs = mock_client.chat.completions.create.call_args[1]
        messages = call_kwargs["messages"]
        assert messages[0]["role"] == "system"
        assert messages[0]["content"] == "You are a diabetes assistant."
        assert messages[1]["role"] == "user"

    @patch("src.integrations.openai_client.openai.AsyncOpenAI")
    async def test_generate_without_system_prompt(self, mock_cls):
        """Test that no system message is added when system prompt is omitted."""
        mock_client = AsyncMock()
        mock_cls.return_value = mock_client
        mock_client.chat.completions.create.return_value = SimpleNamespace(
            choices=[SimpleNamespace(message=SimpleNamespace(content="response"))],
            model="gpt-4o",
            usage=SimpleNamespace(prompt_tokens=5, completion_tokens=3),
        )

        client = OpenAIClient(api_key="sk-test", model="gpt-4o")
        await client.generate(_user_message())

        call_kwargs = mock_client.chat.completions.create.call_args[1]
        messages = call_kwargs["messages"]
        assert len(messages) == 1
        assert messages[0]["role"] == "user"

    @patch("src.integrations.openai_client.openai.AsyncOpenAI")
    async def test_generate_auth_error_raises(self, mock_cls):
        """Test that authentication errors propagate."""
        mock_client = AsyncMock()
        mock_cls.return_value = mock_client
        mock_client.chat.completions.create.side_effect = openai.AuthenticationError(
            message="invalid key",
            response=MagicMock(status_code=401),
            body=None,
        )

        client = OpenAIClient(api_key="sk-bad", model="gpt-4o")
        with pytest.raises(openai.AuthenticationError):
            await client.generate(_user_message())

    @patch("src.integrations.openai_client.openai.AsyncOpenAI")
    async def test_generate_handles_null_usage(self, mock_cls):
        """Test that missing usage info defaults to zero."""
        mock_client = AsyncMock()
        mock_cls.return_value = mock_client
        mock_client.chat.completions.create.return_value = SimpleNamespace(
            choices=[SimpleNamespace(message=SimpleNamespace(content="hi"))],
            model="gpt-4o",
            usage=None,
        )

        client = OpenAIClient(api_key="sk-test", model="gpt-4o")
        result = await client.generate(_user_message())

        assert result.usage.input_tokens == 0
        assert result.usage.output_tokens == 0

    @patch("src.integrations.openai_client.openai.AsyncOpenAI")
    async def test_generate_connection_error_raises(self, mock_cls):
        """Test that connection errors propagate."""
        mock_client = AsyncMock()
        mock_cls.return_value = mock_client
        mock_client.chat.completions.create.side_effect = openai.APIConnectionError(
            request=MagicMock(),
        )

        client = OpenAIClient(api_key="sk-test", model="gpt-4o")
        with pytest.raises(openai.APIConnectionError):
            await client.generate(_user_message())

    @patch("src.integrations.openai_client.openai.AsyncOpenAI")
    async def test_generate_empty_choices_returns_empty_string(self, mock_cls):
        """Test that empty choices list returns empty string."""
        mock_client = AsyncMock()
        mock_cls.return_value = mock_client
        mock_client.chat.completions.create.return_value = SimpleNamespace(
            choices=[],
            model="gpt-4o",
            usage=SimpleNamespace(prompt_tokens=5, completion_tokens=0),
        )

        client = OpenAIClient(api_key="sk-test", model="gpt-4o")
        result = await client.generate(_user_message())

        assert result.content == ""


class TestResponseNormalization:
    """Test that both providers return the same AIResponse shape."""

    @patch("src.integrations.claude.anthropic.AsyncAnthropic")
    @patch("src.integrations.openai_client.openai.AsyncOpenAI")
    async def test_both_providers_return_same_schema(
        self, mock_openai_cls, mock_claude_cls
    ):
        """Test that Claude and OpenAI clients both return AIResponse."""
        # Set up Claude mock
        mock_claude = AsyncMock()
        mock_claude_cls.return_value = mock_claude
        mock_claude.messages.create.return_value = SimpleNamespace(
            content=[SimpleNamespace(text="Claude says hello")],
            model="claude-sonnet-4-5-20250929",
            usage=SimpleNamespace(input_tokens=10, output_tokens=5),
        )

        # Set up OpenAI mock
        mock_openai = AsyncMock()
        mock_openai_cls.return_value = mock_openai
        mock_openai.chat.completions.create.return_value = SimpleNamespace(
            choices=[
                SimpleNamespace(message=SimpleNamespace(content="OpenAI says hello"))
            ],
            model="gpt-4o",
            usage=SimpleNamespace(prompt_tokens=10, completion_tokens=5),
        )

        claude_client = ClaudeClient(api_key="sk-c", model="claude-sonnet-4-5-20250929")
        openai_client = OpenAIClient(api_key="sk-o", model="gpt-4o")

        claude_result = await claude_client.generate(_user_message())
        openai_result = await openai_client.generate(_user_message())

        # Both return AIResponse with the same fields
        for result in [claude_result, openai_result]:
            assert isinstance(result, AIResponse)
            assert isinstance(result.content, str)
            assert isinstance(result.model, str)
            assert isinstance(result.provider, AIProviderType)
            assert hasattr(result.usage, "input_tokens")
            assert hasattr(result.usage, "output_tokens")


class TestAIClientFactory:
    """Tests for get_ai_client factory function."""

    async def test_factory_returns_claude_client(self):
        """Test factory returns ClaudeClient for Claude provider."""
        from src.services.ai_client import get_ai_client

        mock_user = SimpleNamespace(id=uuid.uuid4())
        mock_config = SimpleNamespace(
            provider_type=AIProviderType.CLAUDE,
            encrypted_api_key="encrypted-key",
            model_name=None,
        )

        mock_db = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = mock_config
        mock_db.execute.return_value = mock_result

        with patch(
            "src.services.ai_client.decrypt_credential", return_value="sk-ant-key"
        ):
            client = await get_ai_client(mock_user, mock_db)

        assert isinstance(client, ClaudeClient)
        assert client.model == "claude-sonnet-4-5-20250929"

    async def test_factory_returns_openai_client(self):
        """Test factory returns OpenAIClient for OpenAI provider."""
        from src.services.ai_client import get_ai_client

        mock_user = SimpleNamespace(id=uuid.uuid4())
        mock_config = SimpleNamespace(
            provider_type=AIProviderType.OPENAI,
            encrypted_api_key="encrypted-key",
            model_name="gpt-4o-mini",
        )

        mock_db = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = mock_config
        mock_db.execute.return_value = mock_result

        with patch(
            "src.services.ai_client.decrypt_credential", return_value="sk-openai-key"
        ):
            client = await get_ai_client(mock_user, mock_db)

        assert isinstance(client, OpenAIClient)
        assert client.model == "gpt-4o-mini"

    async def test_factory_raises_404_when_no_provider(self):
        """Test factory raises HTTPException 404 when no provider configured."""
        from fastapi import HTTPException

        from src.services.ai_client import get_ai_client

        mock_user = SimpleNamespace(id=uuid.uuid4())

        mock_db = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = None
        mock_db.execute.return_value = mock_result

        with pytest.raises(HTTPException) as exc_info:
            await get_ai_client(mock_user, mock_db)

        assert exc_info.value.status_code == 404
        assert "No AI provider configured" in exc_info.value.detail

    async def test_factory_uses_custom_model_name(self):
        """Test factory uses model_name from config when set."""
        from src.services.ai_client import get_ai_client

        mock_user = SimpleNamespace(id=uuid.uuid4())
        mock_config = SimpleNamespace(
            provider_type=AIProviderType.CLAUDE,
            encrypted_api_key="encrypted-key",
            model_name="claude-opus-4-6",
        )

        mock_db = AsyncMock()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = mock_config
        mock_db.execute.return_value = mock_result

        with patch(
            "src.services.ai_client.decrypt_credential", return_value="sk-ant-key"
        ):
            client = await get_ai_client(mock_user, mock_db)

        assert client.model == "claude-opus-4-6"
