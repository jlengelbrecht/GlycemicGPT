"""Story 9.2: Brief delivery configuration model.

Stores user preferences for daily brief delivery (time, timezone, channel).
"""

import datetime
import enum
import uuid

from sqlalchemy import Boolean, Enum, ForeignKey, String, Time
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin

# Default delivery time: 07:00
DEFAULT_DELIVERY_TIME = datetime.time(7, 0)
DEFAULT_TIMEZONE = "UTC"


class DeliveryChannel(str, enum.Enum):
    """Channels through which daily briefs can be delivered."""

    WEB_ONLY = "web_only"
    TELEGRAM = "telegram"
    BOTH = "both"


class BriefDeliveryConfig(Base, TimestampMixin):
    """User-specific daily brief delivery configuration.

    One-to-one with User. Stores when and how
    the user receives their daily briefs.
    """

    __tablename__ = "brief_delivery_configs"

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
        nullable=False,
        default=True,
    )

    delivery_time: Mapped[datetime.time] = mapped_column(
        Time,
        nullable=False,
        default=DEFAULT_DELIVERY_TIME,
    )

    timezone: Mapped[str] = mapped_column(
        String(64),
        nullable=False,
        default=DEFAULT_TIMEZONE,
    )

    channel: Mapped[DeliveryChannel] = mapped_column(
        Enum(
            DeliveryChannel,
            name="deliverychannel",
            create_type=False,
            values_callable=lambda e: [member.value for member in e],
        ),
        nullable=False,
        default=DeliveryChannel.BOTH,
    )

    user = relationship("User", back_populates="brief_delivery_config")

    def __repr__(self) -> str:
        ch = self.channel.value if self.channel else "None"
        return (
            f"<BriefDeliveryConfig(user_id={self.user_id}, "
            f"time={self.delivery_time}, tz={self.timezone}, "
            f"channel={ch})>"
        )
