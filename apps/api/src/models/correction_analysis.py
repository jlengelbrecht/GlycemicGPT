"""Story 5.5: Correction factor analysis model.

Stores AI-generated correction factor analyses and ISF adjustment suggestions.
"""

import uuid
from datetime import datetime

from sqlalchemy import DateTime, Float, ForeignKey, Index, Integer, String, Text
from sqlalchemy.dialects.postgresql import JSON, UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin


class CorrectionAnalysis(Base, TimestampMixin):
    """Stores correction factor analyses with ISF adjustment suggestions.

    Each analysis covers a configurable period and evaluates correction bolus
    outcomes grouped by time-of-day period (overnight, morning, afternoon,
    evening). The AI generates specific correction factor adjustment
    suggestions based on under- or over-correction patterns.
    """

    __tablename__ = "correction_analyses"

    __table_args__ = (
        Index("ix_correction_analyses_user_period", "user_id", "period_start"),
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

    # Analysis period
    period_start: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
    )

    period_end: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
    )

    # Aggregate stats
    total_corrections: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
    )

    under_corrections: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
    )

    over_corrections: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
    )

    avg_observed_isf: Mapped[float] = mapped_column(
        Float,
        nullable=False,
    )

    # Per-time-period breakdown (JSON array)
    # [{period, correction_count, under_count, over_count, avg_isf, avg_drop}]
    time_periods_data: Mapped[list] = mapped_column(
        JSON,
        nullable=False,
    )

    # AI output
    ai_analysis: Mapped[str] = mapped_column(
        Text,
        nullable=False,
    )

    ai_model: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
    )

    ai_provider: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
    )

    # Token usage tracking
    input_tokens: Mapped[int] = mapped_column(
        Integer,
        default=0,
        nullable=False,
    )

    output_tokens: Mapped[int] = mapped_column(
        Integer,
        default=0,
        nullable=False,
    )

    # Relationship to user
    user = relationship("User", back_populates="correction_analyses")

    def __repr__(self) -> str:
        return (
            f"<CorrectionAnalysis(user_id={self.user_id}, "
            f"corrections={self.total_corrections}, "
            f"under={self.under_corrections}, over={self.over_corrections})>"
        )
