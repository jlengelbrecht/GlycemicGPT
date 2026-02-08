"""Story 5.2: BYOAI abstraction layer.

Abstract base class and factory for AI provider clients.
Provides a unified interface for generating AI responses
regardless of the underlying provider (Claude or OpenAI).
"""

import abc

from fastapi import HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from src.core.encryption import decrypt_credential
from src.logging_config import get_logger
from src.models.ai_provider import AIProviderConfig, AIProviderType
from src.models.user import User
from src.schemas.ai_response import AIMessage, AIResponse

logger = get_logger(__name__)

# Default models when user has not specified a model_name override
DEFAULT_CLAUDE_MODEL = "claude-sonnet-4-5-20250929"
DEFAULT_OPENAI_MODEL = "gpt-4o"


class BaseAIClient(abc.ABC):
    """Abstract base class for AI provider clients.

    Subclasses implement provider-specific API calls while
    returning a normalized AIResponse.
    """

    def __init__(self, api_key: str, model: str) -> None:
        self._api_key = api_key
        self.model = model

    @abc.abstractmethod
    async def generate(
        self,
        messages: list[AIMessage],
        system_prompt: str | None = None,
        max_tokens: int = 1024,
    ) -> AIResponse:
        """Generate an AI response.

        Args:
            messages: List of conversation messages.
            system_prompt: Optional system-level instruction.
            max_tokens: Maximum tokens in the response.

        Returns:
            Normalized AIResponse with content, model, provider, and usage.
        """


async def get_ai_client(
    user: User,
    db: AsyncSession,
) -> BaseAIClient:
    """Factory that returns the appropriate AI client for a user.

    Loads the user's AI provider configuration, decrypts the API key,
    and returns a configured client instance.

    Args:
        user: The authenticated user.
        db: Database session.

    Returns:
        A configured BaseAIClient subclass instance.

    Raises:
        HTTPException: 404 if no provider is configured.
    """
    from src.integrations.claude import ClaudeClient
    from src.integrations.openai_client import OpenAIClient

    result = await db.execute(
        select(AIProviderConfig).where(AIProviderConfig.user_id == user.id)
    )
    config = result.scalar_one_or_none()

    if not config:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No AI provider configured. Please configure an AI provider first.",
        )

    api_key = decrypt_credential(config.encrypted_api_key)

    if config.provider_type == AIProviderType.CLAUDE:
        model = config.model_name or DEFAULT_CLAUDE_MODEL
        return ClaudeClient(api_key=api_key, model=model)

    if config.provider_type == AIProviderType.OPENAI:
        model = config.model_name or DEFAULT_OPENAI_MODEL
        return OpenAIClient(api_key=api_key, model=model)

    raise HTTPException(
        status_code=status.HTTP_400_BAD_REQUEST,
        detail=f"Unsupported AI provider: {config.provider_type}",
    )
