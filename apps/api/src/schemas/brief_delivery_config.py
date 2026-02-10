"""Story 9.2: Brief delivery configuration schemas."""

import uuid
from datetime import datetime, time

from pydantic import BaseModel, Field, field_validator

from src.models.brief_delivery_config import DeliveryChannel


class BriefDeliveryConfigResponse(BaseModel):
    """Response schema for brief delivery configuration."""

    model_config = {"from_attributes": True}

    id: uuid.UUID
    enabled: bool
    delivery_time: time
    timezone: str
    channel: DeliveryChannel
    updated_at: datetime


class BriefDeliveryConfigUpdate(BaseModel):
    """Request schema for updating brief delivery configuration.

    All fields are optional -- only provided fields are updated.
    """

    enabled: bool | None = None
    delivery_time: time | None = Field(
        default=None,
        description="Delivery time in HH:MM format (e.g. 07:00).",
    )
    timezone: str | None = Field(
        default=None,
        max_length=64,
        description="IANA timezone (e.g. 'America/New_York').",
    )
    channel: DeliveryChannel | None = None

    @field_validator("timezone")
    @classmethod
    def validate_timezone(cls, v: str | None) -> str | None:
        if v is None:
            return v
        import zoneinfo

        try:
            zoneinfo.ZoneInfo(v)
        except (KeyError, zoneinfo.ZoneInfoNotFoundError):
            msg = f"Invalid timezone: {v}"
            raise ValueError(msg)
        return v


class BriefDeliveryConfigDefaults(BaseModel):
    """Default brief delivery configuration values for reference."""

    enabled: bool = True
    delivery_time: time = time(7, 0)
    timezone: str = "UTC"
    channel: DeliveryChannel = DeliveryChannel.BOTH
