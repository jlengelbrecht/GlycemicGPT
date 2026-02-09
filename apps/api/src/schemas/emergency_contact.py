"""Story 6.5: Emergency contact schemas."""

import re
import uuid
from datetime import datetime

from pydantic import BaseModel, Field, field_validator

from src.models.emergency_contact import ContactPriority

TELEGRAM_USERNAME_RE = re.compile(r"^[a-zA-Z0-9_]{5,32}$")


class EmergencyContactCreate(BaseModel):
    """Request schema for creating an emergency contact."""

    name: str = Field(
        ...,
        min_length=1,
        max_length=100,
        description="Contact name (1-100 characters).",
    )
    telegram_username: str = Field(
        ...,
        min_length=5,
        max_length=32,
        description="Telegram username (5-32 alphanumeric/underscore, @ prefix stripped).",
    )
    priority: ContactPriority = Field(
        ...,
        description="Contact priority: primary or secondary.",
    )

    @field_validator("name")
    @classmethod
    def strip_name(cls, v: str) -> str:
        v = v.strip()
        if not v:
            msg = "Name cannot be empty or whitespace only"
            raise ValueError(msg)
        return v

    @field_validator("telegram_username")
    @classmethod
    def validate_telegram_username(cls, v: str) -> str:
        # Strip leading @ if provided
        v = v.lstrip("@").strip()
        if not TELEGRAM_USERNAME_RE.match(v):
            msg = (
                "Telegram username must be 5-32 characters, "
                "alphanumeric or underscores only"
            )
            raise ValueError(msg)
        return v


class EmergencyContactUpdate(BaseModel):
    """Request schema for updating an emergency contact. All fields optional."""

    name: str | None = Field(
        default=None,
        min_length=1,
        max_length=100,
        description="Contact name (1-100 characters).",
    )
    telegram_username: str | None = Field(
        default=None,
        min_length=5,
        max_length=32,
        description="Telegram username (5-32 alphanumeric/underscore, @ prefix stripped).",
    )
    priority: ContactPriority | None = Field(
        default=None,
        description="Contact priority: primary or secondary.",
    )

    @field_validator("name")
    @classmethod
    def strip_name(cls, v: str | None) -> str | None:
        if v is not None:
            v = v.strip()
            if not v:
                msg = "Name cannot be empty or whitespace only"
                raise ValueError(msg)
        return v

    @field_validator("telegram_username")
    @classmethod
    def validate_telegram_username(cls, v: str | None) -> str | None:
        if v is not None:
            v = v.lstrip("@").strip()
            if not TELEGRAM_USERNAME_RE.match(v):
                msg = (
                    "Telegram username must be 5-32 characters, "
                    "alphanumeric or underscores only"
                )
                raise ValueError(msg)
        return v


class EmergencyContactResponse(BaseModel):
    """Response schema for a single emergency contact."""

    model_config = {"from_attributes": True}

    id: uuid.UUID
    name: str
    telegram_username: str
    priority: ContactPriority
    position: int
    created_at: datetime
    updated_at: datetime


class EmergencyContactListResponse(BaseModel):
    """Response schema for listing emergency contacts."""

    contacts: list[EmergencyContactResponse]
    count: int
