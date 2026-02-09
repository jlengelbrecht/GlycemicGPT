"""Story 6.1: Alert threshold configuration model.

Stores user-configured alert thresholds for glucose levels and IoB.
"""

import uuid

from sqlalchemy import Float, ForeignKey
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin


class AlertThreshold(Base, TimestampMixin):
    """User-specific alert threshold configuration.

    One-to-one with User. Stores glucose and IoB thresholds
    that trigger alerts when breached.
    """

    __tablename__ = "alert_thresholds"

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

    # Glucose thresholds (mg/dL)
    low_warning: Mapped[float] = mapped_column(
        Float,
        nullable=False,
        default=70.0,
    )

    urgent_low: Mapped[float] = mapped_column(
        Float,
        nullable=False,
        default=55.0,
    )

    high_warning: Mapped[float] = mapped_column(
        Float,
        nullable=False,
        default=180.0,
    )

    urgent_high: Mapped[float] = mapped_column(
        Float,
        nullable=False,
        default=250.0,
    )

    # Insulin on Board threshold (units)
    iob_warning: Mapped[float] = mapped_column(
        Float,
        nullable=False,
        default=3.0,
    )

    # Relationship to user
    user = relationship("User", back_populates="alert_thresholds")

    def __repr__(self) -> str:
        return (
            f"<AlertThreshold(user_id={self.user_id}, "
            f"low={self.urgent_low}-{self.low_warning}, "
            f"high={self.high_warning}-{self.urgent_high}, "
            f"iob={self.iob_warning})>"
        )
