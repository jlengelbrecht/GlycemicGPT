"""Story 7.1: Telegram link model.

Stores the link between a user's account and their Telegram chat.
"""

import uuid
from datetime import datetime

from sqlalchemy import BigInteger, Boolean, DateTime, ForeignKey, String, func
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin


class TelegramLink(Base, TimestampMixin):
    """Stores the user's linked Telegram chat_id.

    One-to-one with User. Created when the user completes the
    /start verification flow in Telegram.
    """

    __tablename__ = "telegram_links"

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

    chat_id: Mapped[int] = mapped_column(
        BigInteger,
        unique=True,
        nullable=False,
    )

    username: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
    )

    is_verified: Mapped[bool] = mapped_column(
        Boolean,
        nullable=False,
        default=True,
    )

    linked_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False,
    )

    # Relationship to user
    user = relationship("User", back_populates="telegram_link")

    def __repr__(self) -> str:
        return (
            f"<TelegramLink(user_id={self.user_id}, "
            f"chat_id={self.chat_id}, "
            f"username={self.username})>"
        )
