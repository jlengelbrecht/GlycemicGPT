"""Story 9.1: Target glucose range model.

Stores user-configured target glucose range for dashboard display and AI analysis.
"""

import uuid

from sqlalchemy import Float, ForeignKey
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin


class TargetGlucoseRange(Base, TimestampMixin):
    """User-specific target glucose range configuration.

    One-to-one with User. Stores the low and high target values
    used by dashboard displays, reports, and AI suggestions.
    """

    __tablename__ = "target_glucose_ranges"

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

    low_target: Mapped[float] = mapped_column(
        Float,
        nullable=False,
        default=70.0,
    )

    high_target: Mapped[float] = mapped_column(
        Float,
        nullable=False,
        default=180.0,
    )

    user = relationship("User", back_populates="target_glucose_range")

    def __repr__(self) -> str:
        return (
            f"<TargetGlucoseRange(user_id={self.user_id}, "
            f"range={self.low_target}-{self.high_target})>"
        )
