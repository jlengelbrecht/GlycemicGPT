"""Story 5.4: Meal pattern analysis model.

Stores AI-generated post-meal glucose pattern analyses and carb ratio suggestions.
"""

import uuid
from datetime import datetime

from sqlalchemy import DateTime, Float, ForeignKey, Index, Integer, String, Text
from sqlalchemy.dialects.postgresql import JSON, UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin


class MealAnalysis(Base, TimestampMixin):
    """Stores meal pattern analyses with carb ratio suggestions.

    Each analysis covers a configurable period and identifies post-meal
    glucose spike patterns grouped by meal period (breakfast, lunch,
    dinner, snack). The AI generates specific carb ratio adjustment
    suggestions based on the patterns found.
    """

    __tablename__ = "meal_analyses"

    __table_args__ = (Index("ix_meal_analyses_user_period", "user_id", "period_start"),)

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
    total_boluses: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
    )

    total_spikes: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
    )

    avg_post_meal_peak: Mapped[float] = mapped_column(
        Float,
        nullable=False,
    )

    # Per-meal-period breakdown (JSON array)
    # [{period, bolus_count, avg_peak, spike_count, avg_2hr_glucose}]
    meal_periods_data: Mapped[list] = mapped_column(
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
    user = relationship("User", back_populates="meal_analyses")

    def __repr__(self) -> str:
        return (
            f"<MealAnalysis(user_id={self.user_id}, "
            f"boluses={self.total_boluses}, spikes={self.total_spikes})>"
        )
