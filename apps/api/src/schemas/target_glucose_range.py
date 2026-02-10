"""Story 9.1: Target glucose range schemas."""

import uuid
from datetime import datetime

from pydantic import BaseModel, Field, model_validator


class TargetGlucoseRangeResponse(BaseModel):
    """Response schema for target glucose range."""

    model_config = {"from_attributes": True}

    id: uuid.UUID
    low_target: float
    high_target: float
    updated_at: datetime


class TargetGlucoseRangeUpdate(BaseModel):
    """Request schema for updating target glucose range.

    Both fields are optional — only provided fields are updated.
    Validates that low_target < high_target when both are provided.
    """

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

    @model_validator(mode="after")
    def validate_target_ordering(self) -> "TargetGlucoseRangeUpdate":
        """Ensure low_target < high_target when both are provided."""
        if (
            self.low_target is not None
            and self.high_target is not None
            and self.low_target >= self.high_target
        ):
            msg = "low_target must be less than high_target"
            raise ValueError(msg)
        return self


class TargetGlucoseRangeDefaults(BaseModel):
    """Default target glucose range values for reference."""

    low_target: float = 70.0
    high_target: float = 180.0
