"""Story 16.11: Device registration model.

Tracks mobile devices that have registered with the backend
for receiving real-time alert notifications via SSE.
"""

import uuid
from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, String, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base


class DeviceRegistration(Base):
    """Tracks registered mobile devices for alert delivery."""

    __tablename__ = "device_registrations"

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

    device_token: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
        unique=True,
    )

    device_name: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
    )

    platform: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        default="android",
    )

    last_seen_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )

    user = relationship("User", back_populates="device_registrations")

    def __repr__(self) -> str:
        return (
            f"<DeviceRegistration(user_id={self.user_id}, "
            f"device={self.device_name}, platform={self.platform})>"
        )
