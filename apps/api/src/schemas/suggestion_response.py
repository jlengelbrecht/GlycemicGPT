"""Story 5.7: Suggestion response schemas."""

import uuid
from datetime import datetime

from pydantic import BaseModel, Field


class SuggestionResponseRequest(BaseModel):
    """Request to record a user's response to an AI suggestion."""

    response: str = Field(
        ...,
        pattern="^(acknowledged|dismissed)$",
        description="User's response: 'acknowledged' or 'dismissed'",
    )
    reason: str | None = Field(
        default=None,
        max_length=500,
        description="Optional reason for the response",
    )


class SuggestionResponseResponse(BaseModel):
    """Response schema for a recorded suggestion response."""

    model_config = {"from_attributes": True}

    id: uuid.UUID
    analysis_type: str
    analysis_id: uuid.UUID
    response: str
    reason: str | None
    created_at: datetime


class InsightSummary(BaseModel):
    """Summary of an AI insight for display in the frontend."""

    id: uuid.UUID
    analysis_type: str
    title: str
    content: str
    created_at: datetime
    status: str = "pending"  # pending, acknowledged, dismissed


class InsightsListResponse(BaseModel):
    """Response for listing AI insights."""

    insights: list[InsightSummary]
    total: int
