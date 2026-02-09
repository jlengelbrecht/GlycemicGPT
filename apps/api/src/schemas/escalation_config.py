"""Story 6.6: Escalation timing configuration schemas."""

import uuid
from datetime import datetime

from pydantic import BaseModel, Field, model_validator


class EscalationConfigResponse(BaseModel):
    """Response schema for escalation timing configuration."""

    model_config = {"from_attributes": True}

    id: uuid.UUID
    reminder_delay_minutes: int
    primary_contact_delay_minutes: int
    all_contacts_delay_minutes: int
    updated_at: datetime


class EscalationConfigUpdate(BaseModel):
    """Request schema for updating escalation timing.

    All fields are optional — only provided fields are updated.
    Validates that tier ordering is consistent (reminder < primary < all).
    """

    reminder_delay_minutes: int | None = Field(
        default=None,
        ge=2,
        le=60,
        description="Minutes before first reminder (2-60).",
    )
    primary_contact_delay_minutes: int | None = Field(
        default=None,
        ge=2,
        le=120,
        description="Minutes before primary contact alert (2-120).",
    )
    all_contacts_delay_minutes: int | None = Field(
        default=None,
        ge=2,
        le=240,
        description="Minutes before all contacts alert (2-240).",
    )

    @model_validator(mode="after")
    def validate_tier_ordering(self) -> "EscalationConfigUpdate":
        """Ensure reminder < primary_contact < all_contacts when both provided."""
        if (
            self.reminder_delay_minutes is not None
            and self.primary_contact_delay_minutes is not None
            and self.reminder_delay_minutes >= self.primary_contact_delay_minutes
        ):
            msg = (
                "reminder_delay_minutes must be less than primary_contact_delay_minutes"
            )
            raise ValueError(msg)

        if (
            self.primary_contact_delay_minutes is not None
            and self.all_contacts_delay_minutes is not None
            and self.primary_contact_delay_minutes >= self.all_contacts_delay_minutes
        ):
            msg = "primary_contact_delay_minutes must be less than all_contacts_delay_minutes"
            raise ValueError(msg)

        if (
            self.reminder_delay_minutes is not None
            and self.all_contacts_delay_minutes is not None
            and self.reminder_delay_minutes >= self.all_contacts_delay_minutes
        ):
            msg = "reminder_delay_minutes must be less than all_contacts_delay_minutes"
            raise ValueError(msg)

        return self


class EscalationConfigDefaults(BaseModel):
    """Default escalation timing values for reference."""

    reminder_delay_minutes: int = 5
    primary_contact_delay_minutes: int = 10
    all_contacts_delay_minutes: int = 20
