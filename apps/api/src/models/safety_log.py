"""Story 5.6: Safety validation audit log model.

Stores validation decisions for AI-generated suggestions.
"""

import uuid

from sqlalchemy import ForeignKey, Index, String
from sqlalchemy.dialects.postgresql import JSON, UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin


class SafetyLog(Base, TimestampMixin):
    """Audit log for AI suggestion safety validations.

    Records every validation decision including the status
    (approved/flagged/rejected), any flagged items, and the
    analysis that was validated.
    """

    __tablename__ = "safety_logs"

    __table_args__ = (
        Index(
            "ix_safety_logs_user_analysis", "user_id", "analysis_type", "analysis_id"
        ),
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

    # Which analysis was validated
    analysis_type: Mapped[str] = mapped_column(
        String(30),
        nullable=False,
    )

    analysis_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        nullable=False,
    )

    # Validation outcome
    status: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
    )

    # Flagged items detail (JSON array)
    flagged_items: Mapped[list] = mapped_column(
        JSON,
        nullable=False,
        default=list,
    )

    # Whether dangerous content was detected
    has_dangerous_content: Mapped[bool] = mapped_column(
        default=False,
        nullable=False,
    )

    # Relationship to user
    user = relationship("User", back_populates="safety_logs")

    def __repr__(self) -> str:
        return (
            f"<SafetyLog(user_id={self.user_id}, "
            f"type={self.analysis_type}, status={self.status})>"
        )
