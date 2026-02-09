"""Story 6.2: Alert schemas for predictive alert engine.

Response schemas for alert data returned by the API.
"""

from datetime import datetime

from pydantic import BaseModel, ConfigDict


class AlertResponse(BaseModel):
    """Single alert response."""

    model_config = ConfigDict(from_attributes=True)

    id: str
    alert_type: str
    severity: str
    current_value: float
    predicted_value: float | None
    prediction_minutes: int | None
    iob_value: float | None
    message: str
    trend_rate: float | None
    source: str
    acknowledged: bool
    acknowledged_at: datetime | None
    created_at: datetime
    expires_at: datetime


class AlertAcknowledgeResponse(BaseModel):
    """Response after acknowledging an alert."""

    model_config = ConfigDict(from_attributes=True)

    id: str
    acknowledged: bool
    acknowledged_at: datetime | None


class ActiveAlertsResponse(BaseModel):
    """Response for listing active alerts."""

    alerts: list[AlertResponse]
    count: int
