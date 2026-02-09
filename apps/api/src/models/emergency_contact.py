"""Story 6.5: Emergency contact model.

Stores emergency contacts for alert escalation. Each user can have
up to 3 contacts with primary/secondary priority levels.
"""

import enum
import uuid

from sqlalchemy import Enum, ForeignKey, Integer, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin


class ContactPriority(str, enum.Enum):
    """Priority level for emergency contacts."""

    PRIMARY = "primary"
    SECONDARY = "secondary"


class EmergencyContact(Base, TimestampMixin):
    """Emergency contact for alert escalation.

    Each contact has a name, Telegram username, priority level,
    and position for ordering within the same priority.
    """

    __tablename__ = "emergency_contacts"

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

    name: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
    )

    telegram_username: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
    )

    priority: Mapped[ContactPriority] = mapped_column(
        Enum(
            ContactPriority,
            name="contactpriority",
            create_type=False,
            values_callable=lambda e: [member.value for member in e],
        ),
        nullable=False,
    )

    position: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=0,
    )

    # Relationship to user
    user = relationship("User", back_populates="emergency_contacts")

    def __repr__(self) -> str:
        return (
            f"<EmergencyContact(name={self.name!r}, "
            f"telegram=@{self.telegram_username}, "
            f"priority={self.priority.value})>"
        )
