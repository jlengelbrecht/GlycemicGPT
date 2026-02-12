"""Story 3.4 & 3.5: Pump data schemas.

Pydantic schemas for pump event API requests and responses,
including Control-IQ activity data.
"""

from datetime import datetime

from pydantic import BaseModel, ConfigDict

from src.models.pump_data import PumpEventType


class PumpEventResponse(BaseModel):
    """Response schema for a single pump event."""

    model_config = ConfigDict(from_attributes=True)

    event_type: PumpEventType
    event_timestamp: datetime
    units: float | None = None
    duration_minutes: int | None = None
    is_automated: bool = False
    control_iq_reason: str | None = None
    control_iq_mode: str | None = None  # Story 3.5
    basal_adjustment_pct: float | None = None  # Story 3.5
    iob_at_event: float | None = None
    cob_at_event: float | None = None
    bg_at_event: int | None = None
    received_at: datetime
    source: str = "tandem"


class PumpEventHistoryResponse(BaseModel):
    """Response schema for pump event history."""

    events: list[PumpEventResponse]
    count: int


class TandemSyncResponse(BaseModel):
    """Response schema for Tandem sync operation."""

    message: str
    events_fetched: int
    events_stored: int
    profiles_stored: int = 0
    last_event: PumpEventResponse | None = None


class TandemSyncStatusResponse(BaseModel):
    """Response schema for Tandem sync status."""

    integration_status: str
    last_sync_at: datetime | None = None
    last_error: str | None = None
    events_available: int
    latest_event: PumpEventResponse | None = None


class ControlIQActivityResponse(BaseModel):
    """Response schema for Control-IQ activity summary (Story 3.5).

    This provides aggregated metrics about Control-IQ automated actions,
    helping understand what the pump is doing automatically so AI analysis
    can focus on what Control-IQ cannot adjust (carb ratios, correction factors).
    """

    # Event counts
    total_events: int
    automated_events: int
    manual_events: int

    # Correction boluses delivered by Control-IQ
    correction_count: int
    total_correction_units: float

    # Basal rate adjustments
    basal_increase_count: int
    basal_decrease_count: int
    avg_basal_adjustment_pct: float | None = None

    # Insulin suspends (for predicted low)
    suspend_count: int
    automated_suspend_count: int

    # Activity mode usage
    sleep_mode_events: int
    exercise_mode_events: int
    standard_mode_events: int

    # Time range analyzed
    start_time: datetime
    end_time: datetime
    hours_analyzed: int


class IoBProjectionResponse(BaseModel):
    """Response schema for IoB projection (Story 3.7).

    Provides projected insulin-on-board based on the last confirmed value
    and the insulin decay curve for rapid-acting insulins.
    """

    # Last confirmed IoB from pump
    confirmed_iob: float
    confirmed_at: datetime

    # Current projected IoB (accounting for decay since confirmation)
    projected_iob: float
    projected_at: datetime

    # Future projections
    projected_30min: float
    projected_60min: float

    # Data staleness
    minutes_since_confirmed: int
    is_stale: bool
    stale_warning: str | None = None
    is_estimated: bool = False
