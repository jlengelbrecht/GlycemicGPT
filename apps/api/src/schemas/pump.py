"""Story 3.4: Pump data schemas.

Pydantic schemas for pump event API requests and responses.
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
    last_event: PumpEventResponse | None = None


class TandemSyncStatusResponse(BaseModel):
    """Response schema for Tandem sync status."""

    integration_status: str
    last_sync_at: datetime | None = None
    last_error: str | None = None
    events_available: int
    latest_event: PumpEventResponse | None = None
