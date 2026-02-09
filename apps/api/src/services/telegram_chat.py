"""Story 7.5: AI chat via Telegram.

Handles natural language messages from Telegram by routing them
to the user's configured AI provider with recent glucose context.
Each message is a standalone Q&A (no conversation history).
"""

import html
import uuid
from datetime import UTC, datetime, timedelta

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from src.logging_config import get_logger
from src.models.glucose import GlucoseReading
from src.models.user import User
from src.schemas.ai_response import AIMessage
from src.services.ai_client import get_ai_client
from src.services.alert_notifier import trend_description
from src.services.iob_projection import get_iob_projection

logger = get_logger(__name__)

# Telegram message length limit
TELEGRAM_MAX_LENGTH = 4096

# Safety disclaimer appended to every AI response
SAFETY_DISCLAIMER = (
    "\n\n\u26a0\ufe0f <i>Not medical advice. Consult your healthcare provider.</i>"
)

# How many hours of glucose data to include as context
CONTEXT_HOURS = 2

# Maximum readings to fetch for context
CONTEXT_MAX_READINGS = 24

_SYSTEM_PROMPT_PREFIX = """\
You are a supportive diabetes management assistant integrated with GlycemicGPT. \
You help users understand their glucose patterns, discuss insulin management, \
and answer questions about their diabetes care.

Guidelines:
- Be concise (this is a Telegram chat, keep responses under 300 words)
- Be supportive and non-judgmental
- Reference the user's recent glucose data when relevant
- Do NOT recommend specific insulin dose changes
- Do NOT recommend specific carb-to-insulin ratios
- Suggest discussing observations with their endocrinologist
- Use plain text, avoid markdown (Telegram uses HTML)

"""

# Maximum characters we accept from user input before truncating
MAX_USER_MESSAGE_LENGTH = 2000


async def _build_glucose_context(
    db: AsyncSession,
    user_id: uuid.UUID,
) -> str:
    """Build a glucose context string from recent readings.

    Fetches the last CONTEXT_HOURS of glucose readings and IoB data,
    and formats them as a concise summary for the AI system prompt.

    Args:
        db: Database session.
        user_id: User's UUID.

    Returns:
        A formatted string describing recent glucose data, or empty
        string if no data is available.
    """
    cutoff = datetime.now(UTC) - timedelta(hours=CONTEXT_HOURS)

    result = await db.execute(
        select(GlucoseReading)
        .where(
            GlucoseReading.user_id == user_id,
            GlucoseReading.reading_timestamp >= cutoff,
        )
        .order_by(GlucoseReading.reading_timestamp.desc())
        .limit(CONTEXT_MAX_READINGS)
    )
    readings = list(result.scalars().all())

    if not readings:
        return f"Recent glucose data: No readings available in the last {CONTEXT_HOURS} hours."

    latest = readings[0]
    values = [r.value for r in readings]
    min_val = min(values)
    max_val = max(values)
    avg_val = sum(values) / len(values)
    trend = trend_description(latest.trend_rate)

    lines = [
        f"Recent glucose data (last {CONTEXT_HOURS} hours):",
        f"- Current: {latest.value} mg/dL ({trend})",
        f"- Range: {min_val}-{max_val} mg/dL",
        f"- Average: {avg_val:.0f} mg/dL",
        f"- Readings: {len(readings)}",
    ]

    # Add IoB if available
    iob = await get_iob_projection(db, user_id)
    if iob is not None:
        lines.append(f"- Insulin on Board: {iob.projected_iob:.1f} units")
        if iob.is_stale:
            lines.append("- (IoB data is stale, >2 hours old)")

    return "\n".join(lines)


def _build_system_prompt(glucose_context: str) -> str:
    """Build the system prompt with glucose context embedded.

    Uses string concatenation instead of str.format() to avoid
    injection if the glucose context contains brace characters.

    Args:
        glucose_context: Formatted glucose data string.

    Returns:
        Complete system prompt for the AI provider.
    """
    if glucose_context:
        return _SYSTEM_PROMPT_PREFIX + glucose_context
    return _SYSTEM_PROMPT_PREFIX.rstrip()


def _truncate_response(text: str) -> str:
    """Truncate text to fit within Telegram's message length limit.

    Reserves space for the safety disclaimer. If the response
    exceeds the limit, it is truncated with an ellipsis.

    Args:
        text: The full response text (before disclaimer).

    Returns:
        Text truncated to fit within TELEGRAM_MAX_LENGTH with disclaimer.
    """
    max_content_length = TELEGRAM_MAX_LENGTH - len(SAFETY_DISCLAIMER)
    if len(text) <= max_content_length:
        return text + SAFETY_DISCLAIMER
    return text[: max_content_length - 3] + "..." + SAFETY_DISCLAIMER


async def handle_chat(
    db: AsyncSession,
    user_id: uuid.UUID,
    text: str,
) -> str:
    """Process a natural language message through the user's AI provider.

    Fetches recent glucose context, builds a system prompt, and
    generates a response using the user's configured AI provider.
    Always appends a safety disclaimer.

    Args:
        db: Database session.
        user_id: User's UUID.
        text: The user's message text.

    Returns:
        HTML-formatted response string with safety disclaimer.
    """
    # Fetch User object (needed for get_ai_client)
    result = await db.execute(select(User).where(User.id == user_id))
    user = result.scalar_one_or_none()

    if user is None:
        logger.error("User not found for AI chat", user_id=str(user_id))
        return "\u26a0\ufe0f Something went wrong. Please try again later."

    # Get the user's AI client
    try:
        ai_client = await get_ai_client(user, db)
    except HTTPException as exc:
        if exc.status_code == 404:
            return (
                "\u2139\ufe0f No AI provider configured.\n"
                "Set up your AI provider in the GlycemicGPT web app "
                "under Settings to use AI chat."
            )
        logger.error(
            "AI provider configuration error",
            user_id=str(user_id),
            detail=exc.detail,
        )
        return (
            "\u26a0\ufe0f There is an issue with your AI provider configuration. "
            "Please check Settings or try again later."
        )

    # Truncate overly long user messages to limit token usage
    truncated_text = text[:MAX_USER_MESSAGE_LENGTH]

    # Build context and prompt
    try:
        glucose_context = await _build_glucose_context(db, user_id)
    except Exception:
        logger.error(
            "Failed to build glucose context for AI chat",
            user_id=str(user_id),
            exc_info=True,
        )
        glucose_context = "Recent glucose data: unavailable due to a temporary error."
    system_prompt = _build_system_prompt(glucose_context)

    # Generate AI response
    try:
        ai_response = await ai_client.generate(
            messages=[AIMessage(role="user", content=truncated_text)],
            system_prompt=system_prompt,
            max_tokens=800,
        )
    except Exception:
        logger.error(
            "AI provider error in Telegram chat",
            user_id=str(user_id),
            exc_info=True,
        )
        return (
            "\u26a0\ufe0f Unable to get a response from the AI provider. "
            "Please check your API key in Settings or try again later."
        )

    content = ai_response.content.strip()
    if not content:
        return (
            "\u2139\ufe0f The AI returned an empty response. "
            "Please try rephrasing your question." + SAFETY_DISCLAIMER
        )

    # Escape any HTML in the AI response to prevent injection
    safe_content = html.escape(content)

    logger.info(
        "Telegram AI chat response generated",
        user_id=str(user_id),
        model=ai_response.model,
        provider=ai_response.provider.value,
        input_tokens=ai_response.usage.input_tokens,
        output_tokens=ai_response.usage.output_tokens,
    )

    return _truncate_response(safe_content)
