"""Story 7.6: Caregiver-to-patient link model.

Defines the many-to-many relationship between CAREGIVER and DIABETIC users.
A caregiver can be linked to multiple patients; a patient can have
multiple caregivers.
"""

import uuid

from sqlalchemy import CheckConstraint, ForeignKey, UniqueConstraint
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin


class CaregiverLink(Base, TimestampMixin):
    """Links a caregiver user to a patient (diabetic) user.

    Unique constraint on (caregiver_id, patient_id) prevents duplicates.
    """

    __tablename__ = "caregiver_links"
    __table_args__ = (
        UniqueConstraint("caregiver_id", "patient_id", name="uq_caregiver_patient"),
        CheckConstraint("caregiver_id != patient_id", name="ck_no_self_link"),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
    )

    caregiver_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    patient_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    # Permission flags (Story 8.2)
    can_view_glucose: Mapped[bool] = mapped_column(default=True)
    can_view_history: Mapped[bool] = mapped_column(default=True)
    can_view_iob: Mapped[bool] = mapped_column(default=True)
    can_view_ai_suggestions: Mapped[bool] = mapped_column(default=False)
    can_receive_alerts: Mapped[bool] = mapped_column(default=True)

    # Relationships
    caregiver = relationship("User", foreign_keys=[caregiver_id])
    patient = relationship("User", foreign_keys=[patient_id])

    def __repr__(self) -> str:
        return (
            f"<CaregiverLink(caregiver={self.caregiver_id}, patient={self.patient_id})>"
        )
