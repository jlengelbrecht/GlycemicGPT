"""Story 5.1 / 14.2: AI provider configuration schemas.

Pydantic schemas for AI provider configuration endpoints.
Supports 5 provider types with conditional validation.
"""

from datetime import datetime
from urllib.parse import urlparse

from pydantic import BaseModel, Field, model_validator

from src.models.ai_provider import AIProviderStatus, AIProviderType

# Sentinel API key value for providers that don't require authentication
API_KEY_NOT_NEEDED = "not-needed"

# Provider types that require a base_url
_BASE_URL_REQUIRED_TYPES = {
    AIProviderType.CLAUDE_SUBSCRIPTION,
    AIProviderType.CHATGPT_SUBSCRIPTION,
    AIProviderType.OPENAI_COMPATIBLE,
}

# Provider types that require a model_name
_MODEL_REQUIRED_TYPES = {
    AIProviderType.OPENAI_COMPATIBLE,
}

# Legacy provider types that should not be used for new configurations
_LEGACY_TYPES = {
    AIProviderType.CLAUDE,
    AIProviderType.OPENAI,
}

# Provider types that should NOT have a base_url (direct API only)
_DIRECT_API_TYPES = {
    AIProviderType.CLAUDE_API,
    AIProviderType.OPENAI_API,
}


def _validate_base_url(url: str) -> str:
    """Validate base_url to prevent misuse.

    Ensures the URL uses http or https scheme and has a valid hostname.
    Private IPs are allowed since this is a self-hosted homelab application
    where users legitimately point at internal services.

    Args:
        url: The base URL to validate.

    Returns:
        The validated URL.

    Raises:
        ValueError: If the URL is invalid.
    """
    parsed = urlparse(url)

    if parsed.scheme not in ("http", "https"):
        raise ValueError("base_url must use http or https scheme")

    if not parsed.hostname:
        raise ValueError("base_url must contain a valid hostname")

    return url


class AIProviderConfigRequest(BaseModel):
    """Request schema for configuring an AI provider."""

    provider_type: AIProviderType = Field(..., description="AI provider type")
    api_key: str = Field(
        ...,
        min_length=1,
        description=(
            "API key for the selected provider "
            f"(use '{API_KEY_NOT_NEEDED}' for subscription proxies)"
        ),
    )
    model_name: str | None = Field(
        default=None,
        max_length=100,
        description="Model name override (required for openai_compatible)",
    )
    base_url: str | None = Field(
        default=None,
        max_length=500,
        description="Base URL for subscription proxies or self-hosted endpoints",
    )

    @model_validator(mode="after")
    def validate_provider_requirements(self) -> "AIProviderConfigRequest":
        """Validate conditional requirements based on provider type."""
        # Reject legacy provider types for new configurations
        if self.provider_type in _LEGACY_TYPES:
            raise ValueError(
                f"'{self.provider_type.value}' is deprecated. "
                f"Use '{self.provider_type.value}_api' instead."
            )

        if self.provider_type in _BASE_URL_REQUIRED_TYPES and not self.base_url:
            raise ValueError(
                f"base_url is required for {self.provider_type.value} provider"
            )

        if self.provider_type in _MODEL_REQUIRED_TYPES and not self.model_name:
            raise ValueError(
                f"model_name is required for {self.provider_type.value} provider"
            )

        # Validate base_url format when provided
        if self.base_url:
            _validate_base_url(self.base_url)

        # Strip base_url for direct API types (it would be ignored anyway)
        if self.provider_type in _DIRECT_API_TYPES:
            self.base_url = None

        return self


class AIProviderConfigResponse(BaseModel):
    """Response schema for AI provider configuration."""

    model_config = {"from_attributes": True}

    provider_type: AIProviderType
    status: AIProviderStatus
    model_name: str | None = None
    base_url: str | None = None
    masked_api_key: str = Field(
        ..., description="Masked API key showing only last 4 characters"
    )
    last_validated_at: datetime | None = None
    last_error: str | None = None
    created_at: datetime
    updated_at: datetime


class AIProviderTestResponse(BaseModel):
    """Response schema for AI provider key test."""

    success: bool
    message: str


class AIProviderDeleteResponse(BaseModel):
    """Response schema for deleting AI provider configuration."""

    message: str = Field(default="AI provider configuration removed successfully")


class AIChatRequest(BaseModel):
    """Request schema for AI chat messages."""

    message: str = Field(
        ...,
        min_length=1,
        max_length=2000,
        description="The user's question about their glucose data",
    )


class AIChatResponse(BaseModel):
    """Response schema for AI chat messages."""

    response: str = Field(..., description="AI-generated response text")
    disclaimer: str = Field(
        default="Not medical advice. Consult your healthcare provider.",
        description="Safety disclaimer",
    )
