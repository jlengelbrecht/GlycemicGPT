"""Story 5.1: AI provider key validation service.

Validates API keys by making minimal test requests to each provider.
"""

import anthropic
import openai

from src.logging_config import get_logger
from src.models.ai_provider import AIProviderType

logger = get_logger(__name__)

# Validation constants
CLAUDE_VALIDATION_MODEL = "claude-haiku-4-5-20251001"
CLAUDE_VALIDATION_MAX_TOKENS = 1

# Key masking constants
MASK_SUFFIX_LENGTH = 4
MASK_MIN_LENGTH = 5
MASK_PREFIX = "sk-"

# Standardised error message templates
_AUTH_ERROR_TEMPLATE = (
    "Invalid {provider} API key. Please check your key and try again."
)
_CONNECTION_ERROR_TEMPLATE = (
    "Unable to connect to {provider} API. Please try again later."
)
_UNEXPECTED_ERROR_MSG = (
    "An error occurred while validating the API key. Please try again."
)


def validate_ai_api_key(
    provider_type: AIProviderType, api_key: str
) -> tuple[bool, str | None]:
    """Validate an AI provider API key.

    Routes to the appropriate provider validation function.

    Args:
        provider_type: The AI provider to validate against.
        api_key: The API key to test.

    Returns:
        Tuple of (success, error_message).
    """
    if provider_type == AIProviderType.CLAUDE:
        return validate_claude_api_key(api_key)
    elif provider_type == AIProviderType.OPENAI:
        return validate_openai_api_key(api_key)
    else:
        return False, f"Unsupported provider: {provider_type}"


def validate_claude_api_key(api_key: str) -> tuple[bool, str | None]:
    """Validate a Claude (Anthropic) API key.

    Makes a minimal API call to verify the key is valid.

    Args:
        api_key: Anthropic API key to test.

    Returns:
        Tuple of (success, error_message).
    """
    try:
        client = anthropic.Anthropic(api_key=api_key)
        client.messages.create(
            model=CLAUDE_VALIDATION_MODEL,
            max_tokens=CLAUDE_VALIDATION_MAX_TOKENS,
            messages=[{"role": "user", "content": "test"}],
        )
        return True, None
    except anthropic.AuthenticationError:
        logger.warning("Claude API key validation failed - invalid key")
        return False, _AUTH_ERROR_TEMPLATE.format(provider="Claude")
    except anthropic.RateLimitError:
        # Key is valid but rate limited — still means auth succeeded
        logger.info("Claude API key validated (rate limited but authenticated)")
        return True, None
    except anthropic.APIConnectionError as e:
        logger.warning(
            "Claude API key validation failed - connection error",
            error=str(e),
        )
        return False, _CONNECTION_ERROR_TEMPLATE.format(provider="Claude")
    except Exception as e:
        logger.error(
            "Claude API key validation failed - unexpected error",
            error=str(e),
        )
        return False, _UNEXPECTED_ERROR_MSG


def validate_openai_api_key(api_key: str) -> tuple[bool, str | None]:
    """Validate an OpenAI API key.

    Lists models to verify the key is valid.

    Args:
        api_key: OpenAI API key to test.

    Returns:
        Tuple of (success, error_message).
    """
    try:
        client = openai.OpenAI(api_key=api_key)
        client.models.list()
        return True, None
    except openai.AuthenticationError:
        logger.warning("OpenAI API key validation failed - invalid key")
        return False, _AUTH_ERROR_TEMPLATE.format(provider="OpenAI")
    except openai.RateLimitError:
        # Key is valid but rate limited — still means auth succeeded
        logger.info("OpenAI API key validated (rate limited but authenticated)")
        return True, None
    except openai.APIConnectionError as e:
        logger.warning(
            "OpenAI API key validation failed - connection error",
            error=str(e),
        )
        return False, _CONNECTION_ERROR_TEMPLATE.format(provider="OpenAI")
    except Exception as e:
        logger.error(
            "OpenAI API key validation failed - unexpected error",
            error=str(e),
        )
        return False, _UNEXPECTED_ERROR_MSG


def mask_api_key(api_key: str) -> str:
    """Mask an API key showing only the last 4 characters.

    Args:
        api_key: The full API key.

    Returns:
        Masked key like ``sk-...xY7z``.
    """
    if len(api_key) < MASK_MIN_LENGTH:
        return "****"
    prefix = MASK_PREFIX if api_key.startswith(MASK_PREFIX) else ""
    suffix = api_key[-MASK_SUFFIX_LENGTH:]
    return f"{prefix}...{suffix}"
