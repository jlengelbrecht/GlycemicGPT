"""Story 5.1: AI provider configuration schemas.

Pydantic schemas for AI provider configuration endpoints.
"""

from datetime import datetime

from pydantic import BaseModel, Field

from src.models.ai_provider import AIProviderStatus, AIProviderType


class AIProviderConfigRequest(BaseModel):
    """Request schema for configuring an AI provider."""

    provider_type: AIProviderType = Field(
        ..., description="AI provider: 'claude' or 'openai'"
    )
    api_key: str = Field(
        ...,
        min_length=1,
        description="API key for the selected provider",
    )
    model_name: str | None = Field(
        default=None,
        max_length=100,
        description="Optional model name override (e.g., 'claude-sonnet-4-5-20250929', 'gpt-4o')",
    )


class AIProviderConfigResponse(BaseModel):
    """Response schema for AI provider configuration."""

    model_config = {"from_attributes": True}

    provider_type: AIProviderType
    status: AIProviderStatus
    model_name: str | None = None
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
