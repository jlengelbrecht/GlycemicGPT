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


class SafetyInfo(BaseModel):
    """Safety validation info for an analysis."""

    status: str
    has_dangerous_content: bool
    flagged_items: list[dict]
    validated_at: datetime


class ModelInfo(BaseModel):
    """AI model info for an analysis."""

    model: str
    provider: str
    input_tokens: int
    output_tokens: int


class UserResponseInfo(BaseModel):
    """User's response to an analysis."""

    response: str
    reason: str | None
    responded_at: datetime


class InsightDetail(BaseModel):
    """Detailed view of an AI insight including reasoning and audit data."""

    id: uuid.UUID
    analysis_type: str
    title: str
    content: str
    created_at: datetime
    status: str = "pending"

    # Analysis period
    period_start: datetime
    period_end: datetime

    # Data context used for the analysis
    data_context: dict

    # AI model info
    model_info: ModelInfo

    # Safety validation
    safety: SafetyInfo | None = None

    # User response
    user_response: UserResponseInfo | None = None
