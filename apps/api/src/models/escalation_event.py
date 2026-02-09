"""Story 6.7: Escalation event model.

Tracks each escalation action for unacknowledged alerts, creating
an audit trail of when notifications were sent to emergency contacts.
"""

import enum
import uuid
from datetime import UTC, datetime

from sqlalchemy import DateTime, Enum, ForeignKey, Text
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base


class EscalationTier(str, enum.Enum):
    """Tier of escalation."""

    REMINDER = "reminder"
    PRIMARY_CONTACT = "primary_contact"
    ALL_CONTACTS = "all_contacts"


class NotificationStatus(str, enum.Enum):
    """Status of notification delivery."""

    PENDING = "pending"
    SENT = "sent"
    FAILED = "failed"


class EscalationEvent(Base):
    """Records each escalation event for an alert.

    Each alert can have up to 3 escalation events (one per tier).
    The unique constraint on (alert_id, tier) ensures idempotency.
    """

    __tablename__ = "escalation_events"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
    )

    alert_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("alerts.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    tier: Mapped[EscalationTier] = mapped_column(
        Enum(
            EscalationTier,
            name="escalationtier",
            create_type=False,
            values_callable=lambda e: [member.value for member in e],
        ),
        nullable=False,
    )

    triggered_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(UTC),
    )

    message_content: Mapped[str] = mapped_column(
        Text,
        nullable=False,
    )

    notification_status: Mapped[NotificationStatus] = mapped_column(
        Enum(
            NotificationStatus,
            name="notificationstatus",
            create_type=False,
            values_callable=lambda e: [member.value for member in e],
        ),
        nullable=False,
        default=NotificationStatus.PENDING,
    )

    # JSON array of contact UUIDs notified at this tier
    contacts_notified: Mapped[list[str]] = mapped_column(
        JSONB,
        nullable=False,
        default=list,
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(UTC),
    )

    # Relationships
    alert = relationship("Alert", back_populates="escalation_events")
    user = relationship("User", back_populates="escalation_events")

    def __repr__(self) -> str:
        return (
            f"<EscalationEvent(tier={self.tier.value}, "
            f"alert={self.alert_id}, status={self.notification_status.value})>"
        )
