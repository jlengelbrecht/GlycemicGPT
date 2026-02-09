"""Story 5.7: Suggestion response model.

Stores user responses (acknowledge/dismiss) to AI-generated suggestions.
"""

import uuid

from sqlalchemy import ForeignKey, Index, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin


class SuggestionResponse(Base, TimestampMixin):
    """User response to an AI suggestion.

    Records when a user acknowledges or dismisses an AI-generated
    suggestion, enabling feedback tracking and outcome analysis.
    """

    __tablename__ = "suggestion_responses"

    __table_args__ = (
        Index(
            "ix_suggestion_responses_user_analysis",
            "user_id",
            "analysis_type",
            "analysis_id",
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

    # Which analysis the response is for
    analysis_type: Mapped[str] = mapped_column(
        String(30),
        nullable=False,
    )

    analysis_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        nullable=False,
    )

    # User's response
    response: Mapped[str] = mapped_column(
        String(20),
        nullable=False,
    )

    # Optional user feedback
    reason: Mapped[str | None] = mapped_column(
        String(500),
        nullable=True,
    )

    # Relationship to user
    user = relationship("User", back_populates="suggestion_responses")

    def __repr__(self) -> str:
        return (
            f"<SuggestionResponse(user_id={self.user_id}, "
            f"type={self.analysis_type}, response={self.response})>"
        )
