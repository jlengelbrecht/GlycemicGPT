"""Story 8.3: Caregiver dashboard schemas.

Response schemas for the caregiver patient status and glucose history endpoints.
These endpoints return permission-filtered data to caregivers.
"""

import uuid
from datetime import datetime

from pydantic import BaseModel

from src.schemas.caregiver_permissions import CaregiverPermissions


class CaregiverGlucoseData(BaseModel):
    """Current glucose reading data for caregiver view."""

    value: int
    trend: str
    trend_rate: float | None
    reading_timestamp: datetime
    minutes_ago: int
    is_stale: bool


class CaregiverIoBData(BaseModel):
    """Current IoB data for caregiver view.

    ``current_iob`` is the decay-adjusted projected IoB (not the raw pump
    value), accounting for insulin metabolism since last confirmation.
    """

    current_iob: float
    projected_30min: float | None
    confirmed_at: datetime
    is_stale: bool


class CaregiverPatientStatus(BaseModel):
    """Permission-filtered patient status for caregiver dashboard.

    Only includes data sections the caregiver is permitted to see.
    The `permissions` field always shows which data categories are enabled.
    """

    patient_id: uuid.UUID
    patient_email: str
    glucose: CaregiverGlucoseData | None = None
    iob: CaregiverIoBData | None = None
    permissions: CaregiverPermissions


class CaregiverGlucoseHistoryReading(BaseModel):
    """Single glucose reading in history response."""

    value: int
    trend: str
    trend_rate: float | None
    reading_timestamp: datetime


class CaregiverGlucoseHistoryResponse(BaseModel):
    """Glucose history for a patient, returned to a caregiver."""

    patient_id: uuid.UUID
    readings: list[CaregiverGlucoseHistoryReading]
    count: int
