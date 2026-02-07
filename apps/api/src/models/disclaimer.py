"""Disclaimer acknowledgment model.

Story 1.3: First-Run Safety Disclaimer
Stores user acknowledgments of the safety disclaimer.
"""

import uuid
from datetime import datetime

from sqlalchemy import DateTime, String, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from src.models.base import Base, TimestampMixin


class DisclaimerAcknowledgment(Base, TimestampMixin):
    """Records user acknowledgments of the safety disclaimer.

    Before user authentication is implemented (Epic 2), we use a session_id
    to track acknowledgments. This can later be linked to a user account.
    """

    __tablename__ = "disclaimer_acknowledgments"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
    )

    # Session identifier (UUID stored in browser localStorage)
    # Will be linked to user_id once authentication is implemented
    session_id: Mapped[str] = mapped_column(
        String(36),
        unique=True,
        nullable=False,
        index=True,
    )

    # User ID - nullable until Epic 2 (authentication) is implemented
    user_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        nullable=True,
        index=True,
    )

    # Acknowledgment details
    acknowledged_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False,
    )

    # Track which version of disclaimer was acknowledged
    disclaimer_version: Mapped[str] = mapped_column(
        String(10),
        nullable=False,
        default="1.0",
    )

    # IP address for audit purposes
    ip_address: Mapped[str | None] = mapped_column(
        String(45),  # IPv6 max length
        nullable=True,
    )

    # User agent for audit purposes
    user_agent: Mapped[str | None] = mapped_column(
        String(500),
        nullable=True,
    )

    def __repr__(self) -> str:
        return f"<DisclaimerAcknowledgment(session_id={self.session_id}, acknowledged_at={self.acknowledged_at})>"
