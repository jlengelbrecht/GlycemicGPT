"""Story 5.2: OpenAI AI client integration.

Implements the BaseAIClient interface for OpenAI models.
"""

from typing import Any

import openai

from src.logging_config import get_logger
from src.models.ai_provider import AIProviderType
from src.schemas.ai_response import AIMessage, AIResponse, AIUsage
from src.services.ai_client import BaseAIClient

logger = get_logger(__name__)


class OpenAIClient(BaseAIClient):
    """OpenAI AI client using the OpenAI SDK."""

    async def generate(
        self,
        messages: list[AIMessage],
        system_prompt: str | None = None,
        max_tokens: int = 1024,
    ) -> AIResponse:
        """Generate a response using the OpenAI Chat Completions API.

        Args:
            messages: List of conversation messages.
            system_prompt: Optional system-level instruction.
            max_tokens: Maximum tokens in the response.

        Returns:
            Normalised AIResponse.
        """
        client = openai.AsyncOpenAI(api_key=self.api_key)

        openai_messages: list[dict[str, Any]] = []
        if system_prompt:
            openai_messages.append({"role": "system", "content": system_prompt})
        openai_messages.extend({"role": m.role, "content": m.content} for m in messages)

        try:
            response = await client.chat.completions.create(
                model=self.model,
                messages=openai_messages,
                max_tokens=max_tokens,
            )
        except openai.AuthenticationError:
            logger.error("OpenAI API authentication failed during generation")
            raise
        except openai.RateLimitError:
            logger.warning("OpenAI API rate limited during generation")
            raise
        except openai.APIConnectionError as e:
            logger.error("OpenAI API connection error during generation", error=str(e))
            raise
        except Exception as e:
            logger.error("Unexpected error during OpenAI generation", error=str(e))
            raise

        choice = response.choices[0] if response.choices else None
        content = choice.message.content or "" if choice else ""

        usage = AIUsage()
        if response.usage:
            usage = AIUsage(
                input_tokens=response.usage.prompt_tokens,
                output_tokens=response.usage.completion_tokens or 0,
            )

        return AIResponse(
            content=content,
            model=response.model,
            provider=AIProviderType.OPENAI,
            usage=usage,
        )
