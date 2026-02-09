"""Story 6.7: Escalation event schemas."""

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict


class EscalationEventResponse(BaseModel):
    """Single escalation event response."""

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    alert_id: uuid.UUID
    tier: str
    triggered_at: datetime
    message_content: str
    notification_status: str
    contacts_notified: list[str]
    created_at: datetime


class EscalationTimelineResponse(BaseModel):
    """Escalation timeline for an alert."""

    alert_id: uuid.UUID
    events: list[EscalationEventResponse]
    count: int
