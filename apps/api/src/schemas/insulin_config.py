"""Insulin configuration schemas."""

import uuid
from datetime import datetime

from pydantic import BaseModel, Field

# Preset insulin types with their default DIA and onset values
INSULIN_PRESETS: dict[str, dict[str, float]] = {
    "humalog": {"dia_hours": 4.0, "onset_minutes": 15.0},
    "novolog": {"dia_hours": 4.0, "onset_minutes": 15.0},
    "fiasp": {"dia_hours": 3.5, "onset_minutes": 5.0},
    "lyumjev": {"dia_hours": 3.5, "onset_minutes": 5.0},
    "apidra": {"dia_hours": 4.0, "onset_minutes": 15.0},
}


class InsulinConfigResponse(BaseModel):
    """Response schema for insulin configuration."""

    model_config = {"from_attributes": True}

    id: uuid.UUID
    insulin_type: str
    dia_hours: float
    onset_minutes: float
    updated_at: datetime


class InsulinConfigUpdate(BaseModel):
    """Request schema for updating insulin configuration.

    All fields are optional -- only provided fields are updated.
    """

    insulin_type: str | None = Field(
        default=None,
        max_length=50,
        description="Insulin type (e.g. humalog, novolog, fiasp, lyumjev, apidra, custom)",
    )
    dia_hours: float | None = Field(
        default=None,
        ge=2.0,
        le=8.0,
        description="Duration of insulin action in hours. Range: 2-8.",
    )
    onset_minutes: float | None = Field(
        default=None,
        ge=1.0,
        le=60.0,
        description="Insulin onset time in minutes. Range: 1-60.",
    )


class InsulinConfigDefaults(BaseModel):
    """Default insulin configuration values for reference."""

    insulin_type: str = "humalog"
    dia_hours: float = 4.0
    onset_minutes: float = 15.0
    presets: dict[str, dict[str, float]] = INSULIN_PRESETS
