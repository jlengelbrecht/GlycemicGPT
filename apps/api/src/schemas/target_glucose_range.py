"""Target glucose range schemas."""

import uuid
from datetime import datetime

from pydantic import BaseModel, Field, model_validator


class TargetGlucoseRangeResponse(BaseModel):
    """Response schema for target glucose range."""

    model_config = {"from_attributes": True}

    id: uuid.UUID
    urgent_low: float
    low_target: float
    high_target: float
    urgent_high: float
    updated_at: datetime


class TargetGlucoseRangeUpdate(BaseModel):
    """Request schema for updating target glucose range.

    All fields are optional -- only provided fields are updated.
    Validates ordering: urgent_low < low_target < high_target < urgent_high.
    """

    urgent_low: float | None = Field(
        default=None,
        ge=30.0,
        le=70.0,
        description="Urgent low threshold (mg/dL). Range: 30-70.",
    )
    low_target: float | None = Field(
        default=None,
        ge=40.0,
        le=200.0,
        description="Low target threshold (mg/dL). Range: 40-200.",
    )
    high_target: float | None = Field(
        default=None,
        ge=80.0,
        le=400.0,
        description="High target threshold (mg/dL). Range: 80-400.",
    )
    urgent_high: float | None = Field(
        default=None,
        ge=200.0,
        le=500.0,
        description="Urgent high threshold (mg/dL). Range: 200-500.",
    )

    @model_validator(mode="after")
    def validate_target_ordering(self) -> "TargetGlucoseRangeUpdate":
        """Validate ordering of any provided thresholds."""
        pairs = [
            (self.urgent_low, self.low_target, "urgent_low", "low_target"),
            (self.low_target, self.high_target, "low_target", "high_target"),
            (self.high_target, self.urgent_high, "high_target", "urgent_high"),
        ]
        for lower, upper, lower_name, upper_name in pairs:
            if lower is not None and upper is not None and lower >= upper:
                msg = f"{lower_name} must be less than {upper_name}"
                raise ValueError(msg)
        return self


class TargetGlucoseRangeDefaults(BaseModel):
    """Default target glucose range values for reference."""

    urgent_low: float = 55.0
    low_target: float = 70.0
    high_target: float = 180.0
    urgent_high: float = 250.0
