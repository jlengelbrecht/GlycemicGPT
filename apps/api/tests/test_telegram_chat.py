"""Story 7.5: Tests for AI chat via Telegram."""

import uuid
from datetime import UTC, datetime, timedelta
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from src.models.ai_provider import AIProviderType
from src.models.glucose import GlucoseReading, TrendDirection
from src.models.user import User
from src.schemas.ai_response import AIResponse, AIUsage
from src.services.iob_projection import IoBProjection
from src.services.telegram_chat import (
    MAX_USER_MESSAGE_LENGTH,
    SAFETY_DISCLAIMER,
    TELEGRAM_MAX_LENGTH,
    _build_glucose_context,
    _build_system_prompt,
    _truncate_response,
    handle_chat,
)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------
def make_reading(
    value: int = 120,
    trend_rate: float = 0.5,
    minutes_ago: int = 3,
    trend: TrendDirection = TrendDirection.FLAT,
    user_id: uuid.UUID | None = None,
) -> MagicMock:
    """Create a mock GlucoseReading."""
    reading = MagicMock(spec=GlucoseReading)
    reading.value = value
    reading.trend_rate = trend_rate
    reading.trend = trend
    reading.reading_timestamp = datetime.now(UTC) - timedelta(minutes=minutes_ago)
    reading.user_id = user_id or uuid.uuid4()
    return reading


def make_iob(
    projected_iob: float = 2.5,
    is_stale: bool = False,
) -> IoBProjection:
    """Create a real IoBProjection dataclass."""
    now = datetime.now(UTC)
    return IoBProjection(
        confirmed_iob=3.0,
        confirmed_at=now - timedelta(minutes=30),
        projected_iob=projected_iob,
        projected_at=now,
        projected_30min=1.8,
        projected_60min=0.9,
        minutes_since_confirmed=30,
        is_stale=is_stale,
        stale_warning="IoB data is stale" if is_stale else None,
    )


def make_ai_response(content: str = "AI says hello") -> AIResponse:
    """Create a mock AIResponse."""
    return AIResponse(
        content=content,
        model="claude-sonnet-4-5-20250929",
        provider=AIProviderType.CLAUDE,
        usage=AIUsage(input_tokens=100, output_tokens=50),
    )


def make_user(user_id: uuid.UUID | None = None) -> MagicMock:
    """Create a mock User."""
    user = MagicMock(spec=User)
    user.id = user_id or uuid.uuid4()
    user.email = "test@example.com"
    return user


# ---------------------------------------------------------------------------
# _build_glucose_context tests
# ---------------------------------------------------------------------------
class TestBuildGlucoseContext:
    """Tests for _build_glucose_context."""

    @pytest.mark.asyncio
    @patch("src.services.telegram_chat.get_iob_projection", new_callable=AsyncMock)
    async def test_with_readings_and_iob(self, mock_iob):
        mock_iob.return_value = make_iob(projected_iob=2.5)

        readings = [
            make_reading(value=150, trend_rate=-1.5, minutes_ago=0),
            make_reading(value=140, minutes_ago=5),
            make_reading(value=130, minutes_ago=10),
        ]

        mock_scalars = MagicMock()
        mock_scalars.all.return_value = readings
        mock_result = MagicMock()
        mock_result.scalars.return_value = mock_scalars
        db = AsyncMock()
        db.execute.return_value = mock_result

        context = await _build_glucose_context(db, uuid.uuid4())

        assert "150 mg/dL" in context
        assert "falling" in context.lower()
        assert "130-150" in context
        assert "2.5 units" in context
        assert "Readings: 3" in context

    @pytest.mark.asyncio
    @patch("src.services.telegram_chat.get_iob_projection", new_callable=AsyncMock)
    async def test_no_readings(self, mock_iob):
        mock_scalars = MagicMock()
        mock_scalars.all.return_value = []
        mock_result = MagicMock()
        mock_result.scalars.return_value = mock_scalars
        db = AsyncMock()
        db.execute.return_value = mock_result

        context = await _build_glucose_context(db, uuid.uuid4())

        assert "No readings available in the last 2 hours" in context
        mock_iob.assert_not_called()

    @pytest.mark.asyncio
    @patch("src.services.telegram_chat.get_iob_projection", new_callable=AsyncMock)
    async def test_stale_iob_shows_warning(self, mock_iob):
        mock_iob.return_value = make_iob(is_stale=True)

        readings = [make_reading(value=110)]
        mock_scalars = MagicMock()
        mock_scalars.all.return_value = readings
        mock_result = MagicMock()
        mock_result.scalars.return_value = mock_scalars
        db = AsyncMock()
        db.execute.return_value = mock_result

        context = await _build_glucose_context(db, uuid.uuid4())

        assert "stale" in context.lower()

    @pytest.mark.asyncio
    @patch("src.services.telegram_chat.get_iob_projection", new_callable=AsyncMock)
    async def test_no_iob_data(self, mock_iob):
        mock_iob.return_value = None

        readings = [make_reading(value=95)]
        mock_scalars = MagicMock()
        mock_scalars.all.return_value = readings
        mock_result = MagicMock()
        mock_result.scalars.return_value = mock_scalars
        db = AsyncMock()
        db.execute.return_value = mock_result

        context = await _build_glucose_context(db, uuid.uuid4())

        assert "95 mg/dL" in context
        assert "Insulin on Board" not in context


# ---------------------------------------------------------------------------
# _build_system_prompt tests
# ---------------------------------------------------------------------------
class TestBuildSystemPrompt:
    """Tests for _build_system_prompt."""

    def test_includes_glucose_context(self):
        context = "Current: 120 mg/dL (stable)"
        prompt = _build_system_prompt(context)

        assert "120 mg/dL" in prompt
        assert "stable" in prompt

    def test_includes_safety_guidelines(self):
        prompt = _build_system_prompt("")

        assert "NOT recommend specific insulin dose" in prompt
        assert "endocrinologist" in prompt

    def test_includes_telegram_instruction(self):
        prompt = _build_system_prompt("")

        assert "concise" in prompt.lower()
        assert "Telegram" in prompt


# ---------------------------------------------------------------------------
# _truncate_response tests
# ---------------------------------------------------------------------------
class TestTruncateResponse:
    """Tests for _truncate_response."""

    def test_short_response_gets_disclaimer(self):
        result = _truncate_response("Hello world")

        assert result.endswith(SAFETY_DISCLAIMER)
        assert "Hello world" in result

    def test_long_response_truncated_with_ellipsis(self):
        long_text = "x" * TELEGRAM_MAX_LENGTH
        result = _truncate_response(long_text)

        assert len(result) <= TELEGRAM_MAX_LENGTH
        assert "..." in result
        assert result.endswith(SAFETY_DISCLAIMER)

    def test_exact_limit_not_truncated(self):
        max_content = TELEGRAM_MAX_LENGTH - len(SAFETY_DISCLAIMER)
        exact_text = "y" * max_content
        result = _truncate_response(exact_text)

        assert len(result) == TELEGRAM_MAX_LENGTH
        assert "..." not in result


# ---------------------------------------------------------------------------
# handle_chat tests
# ---------------------------------------------------------------------------
class TestHandleChat:
    """Tests for handle_chat."""

    @pytest.mark.asyncio
    @patch("src.services.telegram_chat._build_glucose_context", new_callable=AsyncMock)
    @patch("src.services.telegram_chat.get_ai_client", new_callable=AsyncMock)
    async def test_successful_response(self, mock_get_client, mock_context):
        mock_context.return_value = "Current: 120 mg/dL"

        mock_client = AsyncMock()
        mock_client.generate.return_value = make_ai_response(
            "Your glucose looks stable."
        )
        mock_get_client.return_value = mock_client

        user = make_user()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = user
        db = AsyncMock()
        db.execute.return_value = mock_result

        msg = await handle_chat(db, user.id, "How am I doing?")

        assert "glucose looks stable" in msg
        assert "Not medical advice" in msg
        mock_client.generate.assert_called_once()

    @pytest.mark.asyncio
    @patch("src.services.telegram_chat.get_ai_client", new_callable=AsyncMock)
    async def test_no_ai_provider_returns_friendly_error(self, mock_get_client):
        from fastapi import HTTPException

        mock_get_client.side_effect = HTTPException(
            status_code=404, detail="No AI provider"
        )

        user = make_user()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = user
        db = AsyncMock()
        db.execute.return_value = mock_result

        msg = await handle_chat(db, user.id, "Hello")

        assert "No AI provider configured" in msg
        assert "Settings" in msg

    @pytest.mark.asyncio
    @patch("src.services.telegram_chat._build_glucose_context", new_callable=AsyncMock)
    @patch("src.services.telegram_chat.get_ai_client", new_callable=AsyncMock)
    async def test_ai_error_caught_gracefully(self, mock_get_client, mock_context):
        mock_context.return_value = ""

        mock_client = AsyncMock()
        mock_client.generate.side_effect = ConnectionError("API timeout")
        mock_get_client.return_value = mock_client

        user = make_user()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = user
        db = AsyncMock()
        db.execute.return_value = mock_result

        msg = await handle_chat(db, user.id, "Why am I high?")

        assert "Unable to get a response" in msg

    @pytest.mark.asyncio
    @patch("src.services.telegram_chat._build_glucose_context", new_callable=AsyncMock)
    @patch("src.services.telegram_chat.get_ai_client", new_callable=AsyncMock)
    async def test_safety_disclaimer_appended(self, mock_get_client, mock_context):
        mock_context.return_value = ""

        mock_client = AsyncMock()
        mock_client.generate.return_value = make_ai_response("Some advice here.")
        mock_get_client.return_value = mock_client

        user = make_user()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = user
        db = AsyncMock()
        db.execute.return_value = mock_result

        msg = await handle_chat(db, user.id, "What should I do?")

        assert "Not medical advice" in msg
        assert msg.endswith(SAFETY_DISCLAIMER)

    @pytest.mark.asyncio
    @patch("src.services.telegram_chat._build_glucose_context", new_callable=AsyncMock)
    @patch("src.services.telegram_chat.get_ai_client", new_callable=AsyncMock)
    async def test_long_response_truncated(self, mock_get_client, mock_context):
        mock_context.return_value = ""

        long_content = "a" * 5000
        mock_client = AsyncMock()
        mock_client.generate.return_value = make_ai_response(long_content)
        mock_get_client.return_value = mock_client

        user = make_user()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = user
        db = AsyncMock()
        db.execute.return_value = mock_result

        msg = await handle_chat(db, user.id, "Tell me everything")

        assert len(msg) <= TELEGRAM_MAX_LENGTH
        assert "..." in msg
        assert "Not medical advice" in msg

    @pytest.mark.asyncio
    @patch("src.services.telegram_chat._build_glucose_context", new_callable=AsyncMock)
    @patch("src.services.telegram_chat.get_ai_client", new_callable=AsyncMock)
    async def test_empty_response_handled(self, mock_get_client, mock_context):
        mock_context.return_value = ""

        mock_client = AsyncMock()
        mock_client.generate.return_value = make_ai_response("")
        mock_get_client.return_value = mock_client

        user = make_user()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = user
        db = AsyncMock()
        db.execute.return_value = mock_result

        msg = await handle_chat(db, user.id, "...")

        assert "empty response" in msg.lower()

    @pytest.mark.asyncio
    async def test_user_not_found(self):
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = None
        db = AsyncMock()
        db.execute.return_value = mock_result

        msg = await handle_chat(db, uuid.uuid4(), "Hello")

        assert "Something went wrong" in msg

    @pytest.mark.asyncio
    @patch("src.services.telegram_chat._build_glucose_context", new_callable=AsyncMock)
    @patch("src.services.telegram_chat.get_ai_client", new_callable=AsyncMock)
    async def test_glucose_context_passed_to_system_prompt(
        self, mock_get_client, mock_context
    ):
        mock_context.return_value = "Current: 180 mg/dL (rising)"

        mock_client = AsyncMock()
        mock_client.generate.return_value = make_ai_response("Response")
        mock_get_client.return_value = mock_client

        user = make_user()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = user
        db = AsyncMock()
        db.execute.return_value = mock_result

        await handle_chat(db, user.id, "What's happening?")

        # Verify the system prompt contains glucose context
        call_kwargs = mock_client.generate.call_args
        system_prompt = call_kwargs.kwargs.get(
            "system_prompt", call_kwargs[1].get("system_prompt", "")
        )
        assert "180 mg/dL" in system_prompt

    @pytest.mark.asyncio
    @patch("src.services.telegram_chat._build_glucose_context", new_callable=AsyncMock)
    @patch("src.services.telegram_chat.get_ai_client", new_callable=AsyncMock)
    async def test_html_in_ai_response_is_escaped(self, mock_get_client, mock_context):
        mock_context.return_value = ""

        mock_client = AsyncMock()
        mock_client.generate.return_value = make_ai_response(
            "<script>alert('xss')</script> advice"
        )
        mock_get_client.return_value = mock_client

        user = make_user()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = user
        db = AsyncMock()
        db.execute.return_value = mock_result

        msg = await handle_chat(db, user.id, "Tell me something")

        assert "<script>" not in msg
        assert "&lt;script&gt;" in msg

    @pytest.mark.asyncio
    @patch("src.services.telegram_chat._build_glucose_context", new_callable=AsyncMock)
    @patch("src.services.telegram_chat.get_ai_client", new_callable=AsyncMock)
    async def test_whitespace_only_response_treated_as_empty(
        self, mock_get_client, mock_context
    ):
        mock_context.return_value = ""

        mock_client = AsyncMock()
        mock_client.generate.return_value = make_ai_response("   \n  ")
        mock_get_client.return_value = mock_client

        user = make_user()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = user
        db = AsyncMock()
        db.execute.return_value = mock_result

        msg = await handle_chat(db, user.id, "test")

        assert "empty response" in msg.lower()

    @pytest.mark.asyncio
    @patch("src.services.telegram_chat._build_glucose_context", new_callable=AsyncMock)
    @patch("src.services.telegram_chat.get_ai_client", new_callable=AsyncMock)
    async def test_user_message_passed_to_ai(self, mock_get_client, mock_context):
        """Finding #8: Verify user's question text reaches the AI provider."""
        mock_context.return_value = ""

        mock_client = AsyncMock()
        mock_client.generate.return_value = make_ai_response("Response")
        mock_get_client.return_value = mock_client

        user = make_user()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = user
        db = AsyncMock()
        db.execute.return_value = mock_result

        await handle_chat(db, user.id, "Why am I spiking?")

        call_kwargs = mock_client.generate.call_args
        messages = call_kwargs.kwargs.get(
            "messages", call_kwargs[1].get("messages", [])
        )
        assert len(messages) == 1
        assert messages[0].content == "Why am I spiking?"
        assert messages[0].role == "user"

    @pytest.mark.asyncio
    @patch("src.services.telegram_chat._build_glucose_context", new_callable=AsyncMock)
    @patch("src.services.telegram_chat.get_ai_client", new_callable=AsyncMock)
    async def test_long_user_message_truncated(self, mock_get_client, mock_context):
        """Finding #3: Overly long user messages are truncated before sending to AI."""
        mock_context.return_value = ""

        mock_client = AsyncMock()
        mock_client.generate.return_value = make_ai_response("Response")
        mock_get_client.return_value = mock_client

        user = make_user()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = user
        db = AsyncMock()
        db.execute.return_value = mock_result

        long_message = "x" * (MAX_USER_MESSAGE_LENGTH + 500)
        await handle_chat(db, user.id, long_message)

        call_kwargs = mock_client.generate.call_args
        messages = call_kwargs.kwargs.get(
            "messages", call_kwargs[1].get("messages", [])
        )
        assert len(messages[0].content) == MAX_USER_MESSAGE_LENGTH

    @pytest.mark.asyncio
    @patch("src.services.telegram_chat._build_glucose_context", new_callable=AsyncMock)
    @patch("src.services.telegram_chat.get_ai_client", new_callable=AsyncMock)
    async def test_glucose_context_error_uses_fallback(
        self, mock_get_client, mock_context
    ):
        """Finding #4: DB error in glucose context doesn't crash the handler."""
        mock_context.side_effect = RuntimeError("DB connection lost")

        mock_client = AsyncMock()
        mock_client.generate.return_value = make_ai_response("Still works")
        mock_get_client.return_value = mock_client

        user = make_user()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = user
        db = AsyncMock()
        db.execute.return_value = mock_result

        msg = await handle_chat(db, user.id, "How am I doing?")

        assert "Still works" in msg
        mock_client.generate.assert_called_once()
        # Verify fallback context was used in the system prompt
        call_kwargs = mock_client.generate.call_args
        system_prompt = call_kwargs.kwargs.get(
            "system_prompt", call_kwargs[1].get("system_prompt", "")
        )
        assert "unavailable due to a temporary error" in system_prompt

    @pytest.mark.asyncio
    @patch("src.services.telegram_chat.get_ai_client", new_callable=AsyncMock)
    async def test_unsupported_provider_returns_config_error(self, mock_get_client):
        """Finding #5: HTTP 400 (unsupported provider) gets distinct message."""
        from fastapi import HTTPException

        mock_get_client.side_effect = HTTPException(
            status_code=400, detail="Unsupported AI provider"
        )

        user = make_user()
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = user
        db = AsyncMock()
        db.execute.return_value = mock_result

        msg = await handle_chat(db, user.id, "Hello")

        assert "issue with your AI provider configuration" in msg
        assert "No AI provider configured" not in msg


class TestBuildSystemPromptSafety:
    """Finding #1: Test that braces in glucose context don't break prompt."""

    def test_braces_in_context_are_safe(self):
        context = "Data: {unknown_key} and more {stuff}"
        prompt = _build_system_prompt(context)

        assert "{unknown_key}" in prompt
        assert "{stuff}" in prompt

    def test_empty_context_no_trailing_whitespace(self):
        """Finding #8: Empty context should not produce trailing newlines."""
        prompt = _build_system_prompt("")
        assert not prompt.endswith("\n")
