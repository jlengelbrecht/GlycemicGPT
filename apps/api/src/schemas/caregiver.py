"""Story 8.1: Caregiver invitation and linking schemas."""

import uuid
from datetime import datetime

from pydantic import BaseModel, EmailStr, Field, field_validator


class InvitationCreateResponse(BaseModel):
    """Response after creating a caregiver invitation."""

    id: uuid.UUID
    token: str
    expires_at: datetime
    invite_url: str


class InvitationListItem(BaseModel):
    """Single invitation in a list response."""

    model_config = {"from_attributes": True}

    id: uuid.UUID
    status: str
    created_at: datetime
    expires_at: datetime
    accepted_by_email: str | None = None


class InvitationListResponse(BaseModel):
    """List of caregiver invitations."""

    invitations: list[InvitationListItem]
    count: int


class InvitationDetailResponse(BaseModel):
    """Public invitation details (for acceptance page, no auth)."""

    patient_email: str
    status: str
    expires_at: datetime


class AcceptInvitationRequest(BaseModel):
    """Request to accept a caregiver invitation."""

    token: str = Field(..., min_length=1, max_length=64)
    email: EmailStr
    password: str = Field(
        ...,
        min_length=8,
        max_length=128,
        description="Password (min 8 chars, uppercase, lowercase, number)",
    )

    @field_validator("password")
    @classmethod
    def validate_password_strength(cls, v: str) -> str:
        """Validate password meets strength requirements."""
        import re

        if not re.search(r"[a-z]", v):
            raise ValueError("Password must include uppercase, lowercase, and number")
        if not re.search(r"[A-Z]", v):
            raise ValueError("Password must include uppercase, lowercase, and number")
        if not re.search(r"\d", v):
            raise ValueError("Password must include uppercase, lowercase, and number")
        return v


class AcceptInvitationResponse(BaseModel):
    """Response after accepting an invitation."""

    message: str
    user_id: uuid.UUID


class LinkedPatientResponse(BaseModel):
    """A patient linked to a caregiver."""

    patient_id: uuid.UUID
    patient_email: str
    linked_at: datetime


class LinkedPatientsListResponse(BaseModel):
    """List of linked patients for a caregiver."""

    patients: list[LinkedPatientResponse]
    count: int
