"""Story 16.6: Pump raw event model.

Stores raw BLE history log bytes from the Tandem pump for upload
to the Tandem cloud. Each record maps to one pump event with its
original bytes preserved for faithful re-upload.
"""

import uuid
from datetime import datetime

from sqlalchemy import (
    BigInteger,
    Boolean,
    DateTime,
    ForeignKey,
    Integer,
    Text,
    UniqueConstraint,
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin


class PumpRawEvent(Base, TimestampMixin):
    """Raw BLE history log bytes from the Tandem pump.

    Stored as base64-encoded bytes for direct re-upload to the Tandem cloud.
    The sequence_number is the pump's internal event index and is unique per user.
    """

    __tablename__ = "pump_raw_events"

    __table_args__ = (
        UniqueConstraint("user_id", "sequence_number", name="uq_pump_raw_event_user_seq"),
        {"comment": "Raw BLE bytes from Tandem pump for cloud upload"},
    )

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

    sequence_number: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
    )

    raw_bytes_b64: Mapped[str] = mapped_column(
        Text,
        nullable=False,
    )

    event_type_id: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
    )

    pump_time_seconds: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
    )

    uploaded_to_tandem: Mapped[bool] = mapped_column(
        Boolean,
        default=False,
        nullable=False,
    )

    uploaded_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )

    # Relationship to user
    user = relationship("User", back_populates="pump_raw_events")

    def __repr__(self) -> str:
        return (
            f"<PumpRawEvent(user_id={self.user_id}, seq={self.sequence_number}, "
            f"type_id={self.event_type_id}, uploaded={self.uploaded_to_tandem})>"
        )
