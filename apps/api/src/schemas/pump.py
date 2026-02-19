"""Story 3.4 & 3.5: Pump data schemas.

Pydantic schemas for pump event API requests and responses,
including Control-IQ activity data.
"""

from datetime import UTC, datetime, timedelta

from pydantic import BaseModel, ConfigDict, Field, field_validator

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


class PumpStatusBasal(BaseModel):
    """Latest basal rate from pump."""

    rate: float
    is_automated: bool
    timestamp: datetime


class PumpStatusBattery(BaseModel):
    """Latest battery status from pump."""

    percentage: int
    is_charging: bool
    timestamp: datetime


class PumpStatusReservoir(BaseModel):
    """Latest reservoir level from pump."""

    units_remaining: float
    timestamp: datetime


class PumpStatusResponse(BaseModel):
    """Aggregated latest pump status (basal, battery, reservoir)."""

    basal: PumpStatusBasal | None = None
    battery: PumpStatusBattery | None = None
    reservoir: PumpStatusReservoir | None = None


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


# ============================================================================
# Story 16.5: Mobile Pump Push Schemas
# ============================================================================


class PumpEventPushItem(BaseModel):
    """A single pump event pushed from a mobile client."""

    event_type: PumpEventType
    event_timestamp: datetime = Field(
        ..., description="When the event occurred (ISO-8601 with timezone)"
    )
    units: float | None = None
    duration_minutes: int | None = None
    is_automated: bool = False
    control_iq_mode: str | None = None
    basal_adjustment_pct: float | None = None
    iob_at_event: float | None = None
    bg_at_event: int | None = None

    @field_validator("event_timestamp")
    @classmethod
    def timestamp_not_in_future(cls, v: datetime) -> datetime:
        """Reject timestamps more than 5 minutes in the future."""
        now = datetime.now(UTC)
        if v.tzinfo is None:
            v = v.replace(tzinfo=UTC)
        if v > now + timedelta(minutes=5):
            raise ValueError(
                "event_timestamp cannot be more than 5 minutes in the future"
            )
        return v


class PumpRawEventItem(BaseModel):
    """A single raw BLE history log record from the pump."""

    sequence_number: int = Field(..., ge=0, description="Pump event sequence index")
    raw_bytes_b64: str = Field(
        ...,
        min_length=1,
        max_length=100,
        description="Base64-encoded raw BLE bytes (18-byte record)",
    )
    event_type_id: int = Field(..., ge=0, description="Pump event type ID")
    pump_time_seconds: int = Field(..., ge=0, description="Pump internal timestamp")


class PumpHardwareInfoSchema(BaseModel):
    """Hardware identification from the Tandem pump."""

    serial_number: int = Field(..., gt=0)
    model_number: int = Field(..., gt=0)
    part_number: int = Field(..., gt=0)
    pump_rev: str = Field(..., max_length=50)
    arm_sw_ver: int = Field(..., ge=0)
    msp_sw_ver: int = Field(..., ge=0)
    config_a_bits: int = Field(..., ge=0)
    config_b_bits: int = Field(..., ge=0)
    pcba_sn: int = Field(..., ge=0)
    pcba_rev: str = Field(..., max_length=50)
    pump_features: dict = Field(
        default_factory=dict, description="Feature flags (dexcomG5, controlIQ, etc.)"
    )


class PumpPushRequest(BaseModel):
    """Batch of pump events pushed from a mobile client."""

    events: list[PumpEventPushItem] = Field(
        ..., min_length=1, max_length=100, description="Pump events to push (1-100)"
    )
    raw_events: list[PumpRawEventItem] | None = Field(
        default=None, max_length=500, description="Raw BLE bytes for Tandem upload"
    )
    pump_info: PumpHardwareInfoSchema | None = Field(
        default=None, description="Pump hardware identification"
    )
    source: str = Field(default="mobile", max_length=50)


class PumpPushResponse(BaseModel):
    """Response after processing a pump push request."""

    accepted: int = Field(..., description="Number of new events stored")
    duplicates: int = Field(..., description="Number of duplicate events skipped")
    raw_accepted: int = Field(
        default=0, description="Number of raw events stored for Tandem upload"
    )
    raw_duplicates: int = Field(
        default=0, description="Number of duplicate raw events skipped"
    )


# ============================================================================
# Story 16.6: Tandem Cloud Upload Schemas
# ============================================================================


class TandemUploadStatusResponse(BaseModel):
    """Status of Tandem cloud upload for the current user."""

    enabled: bool
    upload_interval_minutes: int
    last_upload_at: datetime | None = None
    last_upload_status: str | None = None
    last_error: str | None = None
    max_event_index_uploaded: int = 0
    pending_raw_events: int = 0


class TandemUploadSettingsRequest(BaseModel):
    """Request to update Tandem cloud upload settings."""

    enabled: bool
    interval_minutes: int = Field(
        default=15, description="Upload interval in minutes (5, 10, or 15)"
    )

    @field_validator("interval_minutes")
    @classmethod
    def validate_interval(cls, v: int) -> int:
        if v not in (5, 10, 15):
            raise ValueError("interval_minutes must be 5, 10, or 15")
        return v


class TandemUploadTriggerResponse(BaseModel):
    """Response after triggering a manual Tandem upload."""

    message: str
    events_uploaded: int = 0
    status: str = "pending"
