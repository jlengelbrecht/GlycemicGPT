"""Story 8.2: Caregiver permission schemas."""

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict


class CaregiverPermissions(BaseModel):
    """The 5 per-caregiver permission flags."""

    can_view_glucose: bool = True
    can_view_history: bool = True
    can_view_iob: bool = True
    can_view_ai_suggestions: bool = False
    can_receive_alerts: bool = True


class LinkedCaregiverItem(BaseModel):
    """A caregiver linked to the current patient, with permissions."""

    link_id: uuid.UUID
    caregiver_id: uuid.UUID
    caregiver_email: str
    linked_at: datetime
    permissions: CaregiverPermissions


class LinkedCaregiversResponse(BaseModel):
    """List of linked caregivers for a diabetic user."""

    caregivers: list[LinkedCaregiverItem]
    count: int


class PermissionsUpdateRequest(BaseModel):
    """Partial update of caregiver permissions.

    Only provided fields are updated; omitted fields remain unchanged.
    """

    model_config = ConfigDict(extra="forbid")

    can_view_glucose: bool | None = None
    can_view_history: bool | None = None
    can_view_iob: bool | None = None
    can_view_ai_suggestions: bool | None = None
    can_receive_alerts: bool | None = None


class PermissionsUpdateResponse(BaseModel):
    """Response after updating permissions."""

    link_id: uuid.UUID
    permissions: CaregiverPermissions
