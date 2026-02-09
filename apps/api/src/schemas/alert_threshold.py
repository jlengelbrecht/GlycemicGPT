"""Story 6.1: Alert threshold schemas."""

import uuid
from datetime import datetime

from pydantic import BaseModel, Field, model_validator


class AlertThresholdResponse(BaseModel):
    """Response schema for alert thresholds."""

    model_config = {"from_attributes": True}

    id: uuid.UUID
    low_warning: float
    urgent_low: float
    high_warning: float
    urgent_high: float
    iob_warning: float
    updated_at: datetime


class AlertThresholdUpdate(BaseModel):
    """Request schema for updating alert thresholds.

    All fields are optional — only provided fields are updated.
    Validates that threshold ordering is consistent.
    """

    low_warning: float | None = Field(
        default=None,
        ge=40.0,
        le=100.0,
        description="Low warning threshold (mg/dL). Range: 40-100.",
    )
    urgent_low: float | None = Field(
        default=None,
        ge=30.0,
        le=80.0,
        description="Urgent low threshold (mg/dL). Range: 30-80.",
    )
    high_warning: float | None = Field(
        default=None,
        ge=120.0,
        le=300.0,
        description="High warning threshold (mg/dL). Range: 120-300.",
    )
    urgent_high: float | None = Field(
        default=None,
        ge=150.0,
        le=400.0,
        description="Urgent high threshold (mg/dL). Range: 150-400.",
    )
    iob_warning: float | None = Field(
        default=None,
        ge=0.5,
        le=20.0,
        description="IoB warning threshold (units). Range: 0.5-20.0.",
    )

    @model_validator(mode="after")
    def validate_threshold_ordering(self) -> "AlertThresholdUpdate":
        """Ensure urgent_low < low_warning and high_warning < urgent_high."""
        if (
            self.urgent_low is not None
            and self.low_warning is not None
            and self.urgent_low >= self.low_warning
        ):
            msg = "urgent_low must be less than low_warning"
            raise ValueError(msg)

        if (
            self.high_warning is not None
            and self.urgent_high is not None
            and self.high_warning >= self.urgent_high
        ):
            msg = "high_warning must be less than urgent_high"
            raise ValueError(msg)

        return self


class AlertThresholdDefaults(BaseModel):
    """Default threshold values for reference."""

    low_warning: float = 70.0
    urgent_low: float = 55.0
    high_warning: float = 180.0
    urgent_high: float = 250.0
    iob_warning: float = 3.0
