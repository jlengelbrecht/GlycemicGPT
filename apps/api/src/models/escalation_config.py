"""Story 6.6: Escalation timing configuration model.

Stores user-configured escalation timing for alert escalation to contacts.
"""

import uuid

from sqlalchemy import ForeignKey, Integer
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin


class EscalationConfig(Base, TimestampMixin):
    """User-specific escalation timing configuration.

    One-to-one with User. Stores the delay (in minutes) before each
    escalation tier triggers:
    1. Reminder to user
    2. Alert to primary emergency contact
    3. Alert to all emergency contacts
    """

    __tablename__ = "escalation_configs"

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

    # Minutes before a reminder is sent to the user
    reminder_delay_minutes: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=5,
    )

    # Minutes before primary emergency contact is alerted
    primary_contact_delay_minutes: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=10,
    )

    # Minutes before all emergency contacts are alerted
    all_contacts_delay_minutes: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        default=20,
    )

    # Relationship to user
    user = relationship("User", back_populates="escalation_config")

    def __repr__(self) -> str:
        return (
            f"<EscalationConfig(user_id={self.user_id}, "
            f"reminder={self.reminder_delay_minutes}m, "
            f"primary={self.primary_contact_delay_minutes}m, "
            f"all={self.all_contacts_delay_minutes}m)>"
        )
