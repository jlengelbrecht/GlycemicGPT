"""Story 15.8: Pump profile model.

Stores pump settings profiles synced from Tandem t:connect, including
time-segmented basal rates, correction factors, carb ratios, and target BG.
"""

import uuid
from datetime import datetime

from sqlalchemy import (
    Boolean,
    DateTime,
    Float,
    ForeignKey,
    Integer,
    String,
    UniqueConstraint,
)
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin


class PumpProfile(Base, TimestampMixin):
    """Stores a pump settings profile from Tandem t:connect.

    Each profile contains time-segmented insulin delivery settings
    (basal rates, correction factors, carb ratios, target BG) stored
    as a JSONB array in the segments column.
    """

    __tablename__ = "pump_profiles"

    __table_args__ = (
        UniqueConstraint("user_id", "profile_name", name="uq_pump_profile_user_name"),
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

    profile_name: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
    )

    is_active: Mapped[bool] = mapped_column(
        Boolean,
        default=False,
        nullable=False,
    )

    # Time-segmented settings as JSONB array
    # Each element: {time, start_minutes, basal_rate, correction_factor,
    #                carb_ratio, target_bg}
    segments: Mapped[list] = mapped_column(
        JSONB,
        nullable=False,
        default=list,
    )

    insulin_duration_min: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
    )

    carb_entry_enabled: Mapped[bool] = mapped_column(
        Boolean,
        default=True,
        nullable=False,
    )

    max_bolus_units: Mapped[float | None] = mapped_column(
        Float,
        nullable=True,
    )

    cgm_high_alert_mgdl: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
    )

    cgm_low_alert_mgdl: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
    )

    synced_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
    )

    user = relationship("User", back_populates="pump_profiles")

    def __repr__(self) -> str:
        return (
            f"<PumpProfile(user_id={self.user_id}, name={self.profile_name!r}, "
            f"active={self.is_active}, segments={len(self.segments or [])})>"
        )
