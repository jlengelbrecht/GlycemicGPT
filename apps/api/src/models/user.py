"""Story 2.1: User Registration - User Model.

Defines the User model with role-based access control.
"""

import enum
import uuid
from datetime import datetime

from sqlalchemy import DateTime, Enum, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin


class UserRole(str, enum.Enum):
    """User roles for role-based access control.

    - DIABETIC: Primary user, can view own data and receive AI suggestions
    - CAREGIVER: Can view linked patient data, cannot modify settings
    - ADMIN: Full system access including health and configuration
    """

    DIABETIC = "diabetic"
    CAREGIVER = "caregiver"
    ADMIN = "admin"


class User(Base, TimestampMixin):
    """User account model.

    Attributes:
        id: Unique user identifier (UUID)
        email: User's email address (unique, used for login)
        hashed_password: Bcrypt-hashed password
        role: User role (diabetic, caregiver, admin)
        is_active: Whether the account is active
        email_verified: Whether the email has been verified
        disclaimer_acknowledged: Whether user has acknowledged the disclaimer
        last_login_at: Timestamp of last successful login
    """

    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
    )
    email: Mapped[str] = mapped_column(
        String(255),
        unique=True,
        nullable=False,
        index=True,
    )
    hashed_password: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
    )
    role: Mapped[UserRole] = mapped_column(
        Enum(
            UserRole,
            name="userrole",
            create_type=False,  # Already created in migration
            values_callable=lambda e: [member.value for member in e],
        ),
        nullable=False,
        default=UserRole.DIABETIC,
    )
    is_active: Mapped[bool] = mapped_column(
        default=True,
    )
    email_verified: Mapped[bool] = mapped_column(
        default=False,
    )
    disclaimer_acknowledged: Mapped[bool] = mapped_column(
        default=False,
    )
    last_login_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )

    # Relationships
    integrations = relationship(
        "IntegrationCredential",
        back_populates="user",
        cascade="all, delete-orphan",
    )
    glucose_readings = relationship(
        "GlucoseReading",
        back_populates="user",
        cascade="all, delete-orphan",
    )
    pump_events = relationship(
        "PumpEvent",
        back_populates="user",
        cascade="all, delete-orphan",
    )
    ai_provider_config = relationship(
        "AIProviderConfig",
        back_populates="user",
        cascade="all, delete-orphan",
        uselist=False,
    )
    daily_briefs = relationship(
        "DailyBrief",
        back_populates="user",
        cascade="all, delete-orphan",
    )
    meal_analyses = relationship(
        "MealAnalysis",
        back_populates="user",
        cascade="all, delete-orphan",
    )
    correction_analyses = relationship(
        "CorrectionAnalysis",
        back_populates="user",
        cascade="all, delete-orphan",
    )
    safety_logs = relationship(
        "SafetyLog",
        back_populates="user",
        cascade="all, delete-orphan",
    )

    def __repr__(self) -> str:
        return f"<User {self.email} ({self.role.value})>"
