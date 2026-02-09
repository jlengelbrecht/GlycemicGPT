"""Story 8.1: Caregiver invitation model.

Token-based invitations for linking caregivers to patients.
A diabetic user creates an invitation with a unique token;
the caregiver visits the invite URL, registers (or links), and
the invitation is marked as accepted.
"""

import secrets
import uuid
from datetime import UTC, datetime, timedelta
from enum import Enum

from sqlalchemy import DateTime, ForeignKey, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin

INVITATION_EXPIRY_DAYS = 7
TOKEN_BYTES = 32


class InvitationStatus(str, Enum):
    """Lifecycle status of a caregiver invitation."""

    PENDING = "pending"
    ACCEPTED = "accepted"
    EXPIRED = "expired"
    REVOKED = "revoked"


def _generate_token() -> str:
    return secrets.token_urlsafe(TOKEN_BYTES)


def _default_expiry() -> datetime:
    return datetime.now(UTC) + timedelta(days=INVITATION_EXPIRY_DAYS)


class CaregiverInvitation(Base, TimestampMixin):
    """Invitation for a caregiver to link with a patient.

    The patient (diabetic user) creates the invitation, which generates
    a unique token. The caregiver visits the invite URL, registers or
    links their existing account, and the invitation status moves to
    ACCEPTED.
    """

    __tablename__ = "caregiver_invitations"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
    )

    patient_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    token: Mapped[str] = mapped_column(
        String(64),
        unique=True,
        nullable=False,
        default=_generate_token,
        index=True,
    )

    status: Mapped[str] = mapped_column(
        String(16),
        nullable=False,
        default=InvitationStatus.PENDING.value,
    )

    expires_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=_default_expiry,
    )

    accepted_by: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="SET NULL"),
        nullable=True,
        default=None,
    )

    accepted_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
        default=None,
    )

    # Relationships
    patient = relationship("User", foreign_keys=[patient_id])
    acceptor = relationship("User", foreign_keys=[accepted_by])

    def __repr__(self) -> str:
        return (
            f"<CaregiverInvitation(id={self.id}, "
            f"patient={self.patient_id}, status={self.status})>"
        )
