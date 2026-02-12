"""Insulin configuration model.

Stores user-configured insulin type and duration of insulin action (DIA)
for IoB decay calculations.
"""

import uuid

from sqlalchemy import Float, ForeignKey, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin


class InsulinConfig(Base, TimestampMixin):
    """User-specific insulin configuration.

    One-to-one with User. Stores the insulin type and DIA used
    for IoB projection calculations.
    """

    __tablename__ = "insulin_configs"

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

    insulin_type: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
        default="humalog",
    )

    dia_hours: Mapped[float] = mapped_column(
        Float,
        nullable=False,
        default=4.0,
    )

    onset_minutes: Mapped[float] = mapped_column(
        Float,
        nullable=False,
        default=15.0,
    )

    user = relationship("User", back_populates="insulin_config")

    def __repr__(self) -> str:
        return (
            f"<InsulinConfig(user_id={self.user_id}, "
            f"type={self.insulin_type}, dia={self.dia_hours}h)>"
        )
