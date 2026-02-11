"""Story 5.1: AI provider configuration model.

Models for storing per-user AI provider settings with encrypted API keys.
"""

import enum
import uuid
from datetime import datetime

from sqlalchemy import DateTime, Enum, ForeignKey, String, Text, UniqueConstraint
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin


class AIProviderType(str, enum.Enum):
    """Supported AI provider types."""

    # Direct API (pay-per-token)
    CLAUDE_API = "claude_api"
    OPENAI_API = "openai_api"

    # Subscription proxies (unlimited usage via proxy)
    CLAUDE_SUBSCRIPTION = "claude_subscription"
    CHATGPT_SUBSCRIPTION = "chatgpt_subscription"

    # Self-hosted / generic OpenAI-compatible endpoint
    OPENAI_COMPATIBLE = "openai_compatible"

    # Legacy values kept for backwards compatibility with existing DB rows
    # Migration 028 updates these, but enum values can't be removed in PG
    CLAUDE = "claude"
    OPENAI = "openai"


class AIProviderStatus(str, enum.Enum):
    """AI provider connection status."""

    CONNECTED = "connected"
    ERROR = "error"
    PENDING = "pending"


class AIProviderConfig(Base, TimestampMixin):
    """Stores per-user AI provider configuration with encrypted API key.

    Each user can have one active AI provider configuration.
    API keys are encrypted using Fernet symmetric encryption.
    """

    __tablename__ = "ai_provider_configs"

    # One active AI provider per user (user picks Claude or OpenAI, not both)
    __table_args__ = (UniqueConstraint("user_id", name="uq_ai_provider_user"),)

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    provider_type: Mapped[AIProviderType] = mapped_column(
        Enum(
            AIProviderType,
            name="aiprovidertype",
            create_type=False,
            values_callable=lambda e: [member.value for member in e],
        ),
        nullable=False,
    )

    # Encrypted API key
    encrypted_api_key: Mapped[str] = mapped_column(
        Text,
        nullable=False,
    )

    # Optional model name override (e.g., "claude-sonnet-4-5-20250929", "gpt-4o")
    model_name: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
    )

    # Base URL for subscription proxies and self-hosted endpoints
    base_url: Mapped[str | None] = mapped_column(
        String(500),
        nullable=True,
    )

    # Connection status
    status: Mapped[AIProviderStatus] = mapped_column(
        Enum(
            AIProviderStatus,
            name="aiproviderstatus",
            create_type=False,
            values_callable=lambda e: [member.value for member in e],
        ),
        nullable=False,
        default=AIProviderStatus.CONNECTED,
    )

    # Last validation timestamp
    last_validated_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )

    # Last error message (if any)
    last_error: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
    )

    # Relationship to user
    user = relationship("User", back_populates="ai_provider_config")

    def __repr__(self) -> str:
        return f"<AIProviderConfig(user_id={self.user_id}, provider={self.provider_type.value}, status={self.status.value})>"
