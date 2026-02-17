"""Story 16.6: Tandem cloud upload state model.

Tracks per-user upload configuration, cached auth tokens,
and incremental sync state for the Tandem cloud upload pipeline.
"""

import uuid
from datetime import datetime

from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin


class TandemUploadState(Base, TimestampMixin):
    """Per-user state for Tandem cloud upload.

    Tracks whether upload is enabled, the upload interval, cached
    Tandem OAuth tokens, and the max event index already uploaded
    (for incremental sync via getLastEventUploaded).
    """

    __tablename__ = "tandem_upload_state"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        unique=True,
        index=True,
    )

    enabled: Mapped[bool] = mapped_column(
        Boolean,
        default=False,
        nullable=False,
    )

    upload_interval_minutes: Mapped[int] = mapped_column(
        Integer,
        default=15,
        nullable=False,
    )

    last_upload_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )

    last_upload_status: Mapped[str | None] = mapped_column(
        String(20),
        nullable=True,
    )

    last_error: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
    )

    max_event_index_uploaded: Mapped[int] = mapped_column(
        Integer,
        default=0,
        nullable=False,
    )

    # Encrypted cached Tandem OAuth tokens
    tandem_access_token: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
    )

    tandem_refresh_token: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
    )

    tandem_token_expires_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )

    # Tandem pumperId from JWT claims (for deviceAssignmentId in uploads)
    tandem_pumper_id: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
    )

    # Relationship to user
    user = relationship("User", back_populates="tandem_upload_state")

    def __repr__(self) -> str:
        return (
            f"<TandemUploadState(user_id={self.user_id}, "
            f"enabled={self.enabled}, last_status={self.last_upload_status})>"
        )
