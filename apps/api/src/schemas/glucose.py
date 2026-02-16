"""Story 3.2: Glucose reading schemas.

Pydantic schemas for glucose reading API responses.
"""

from datetime import datetime

from pydantic import BaseModel, Field

from src.models.glucose import TrendDirection


class GlucoseReadingResponse(BaseModel):
    """Response schema for a single glucose reading."""

    model_config = {"from_attributes": True}

    value: int = Field(..., description="Glucose value in mg/dL", ge=20, le=600)
    reading_timestamp: datetime = Field(..., description="When the reading was taken")
    trend: TrendDirection = Field(..., description="Trend direction")
    trend_rate: float | None = Field(None, description="Rate of change in mg/dL/min")
    received_at: datetime = Field(..., description="When we received the reading")
    source: str = Field(..., description="Source device/integration")


class CurrentGlucoseResponse(BaseModel):
    """Response schema for current glucose status."""

    value: int = Field(..., description="Current glucose value in mg/dL")
    trend: TrendDirection = Field(..., description="Current trend direction")
    trend_rate: float | None = Field(None, description="Rate of change in mg/dL/min")
    reading_timestamp: datetime = Field(..., description="When the reading was taken")
    minutes_ago: int = Field(..., description="Minutes since reading")
    is_stale: bool = Field(
        ..., description="True if reading is more than 10 minutes old"
    )


class GlucoseHistoryResponse(BaseModel):
    """Response schema for glucose history."""

    readings: list[GlucoseReadingResponse]
    count: int = Field(..., description="Number of readings returned")


class TimeInRangeResponse(BaseModel):
    """Response schema for time-in-range statistics."""

    low_pct: float = Field(..., description="Percentage of readings below range")
    in_range_pct: float = Field(..., description="Percentage of readings in range")
    high_pct: float = Field(..., description="Percentage of readings above range")
    readings_count: int = Field(..., description="Total readings analyzed")
    low_threshold: float = Field(..., description="Low target threshold (mg/dL)")
    high_threshold: float = Field(..., description="High target threshold (mg/dL)")


class SyncResponse(BaseModel):
    """Response schema for sync operation."""

    message: str = Field(..., description="Status message")
    readings_fetched: int = Field(
        ..., description="Number of readings fetched from Dexcom"
    )
    readings_stored: int = Field(..., description="Number of new readings stored")
    last_reading: GlucoseReadingResponse | None = Field(
        None, description="Most recent reading"
    )


class SyncStatusResponse(BaseModel):
    """Response schema for sync status."""

    integration_status: str = Field(..., description="Integration connection status")
    last_sync_at: datetime | None = Field(None, description="Last successful sync time")
    last_error: str | None = Field(None, description="Last error message if any")
    readings_available: int = Field(..., description="Number of readings in database")
    latest_reading: GlucoseReadingResponse | None = Field(
        None, description="Most recent reading"
    )
