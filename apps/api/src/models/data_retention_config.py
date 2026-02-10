"""Story 9.3: Data retention configuration model.

Stores user-configured data retention periods for glucose data,
AI analysis results, and audit logs.
"""

import uuid

from sqlalchemy import ForeignKey, Integer
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin

# Default retention periods in days
DEFAULT_GLUCOSE_RETENTION_DAYS = 365  # 1 year
DEFAULT_ANALYSIS_RETENTION_DAYS = 365  # 1 year
DEFAULT_AUDIT_RETENTION_DAYS = 730  # 2 years


class DataRetentionConfig(Base, TimestampMixin):
    """User-specific data retention configuration.

    One-to-one with User. Controls how long different categories of data
    are retained before being eligible for automatic cleanup.

    Categories:
        - glucose: GlucoseReading, PumpEvent records
        - analysis: DailyBrief, MealAnalysis, CorrectionAnalysis, SuggestionResponse
        - audit: SafetyLog, Alert, EscalationEvent
    """

    __tablename__ = "data_retention_configs"

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

    glucose_retention_days: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=DEFAULT_GLUCOSE_RETENTION_DAYS,
    )

    analysis_retention_days: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=DEFAULT_ANALYSIS_RETENTION_DAYS,
    )

    audit_retention_days: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=DEFAULT_AUDIT_RETENTION_DAYS,
    )

    user = relationship("User", back_populates="data_retention_config")

    def __repr__(self) -> str:
        return (
            f"<DataRetentionConfig(user_id={self.user_id}, "
            f"glucose={self.glucose_retention_days}d, "
            f"analysis={self.analysis_retention_days}d, "
            f"audit={self.audit_retention_days}d)>"
        )
