"""Story 5.5: Correction factor analysis schemas.

Request and response schemas for correction factor outcome analysis
and ISF adjustment suggestions.
"""

import uuid
from datetime import datetime

from pydantic import BaseModel, Field


class TimePeriodData(BaseModel):
    """Metrics for a single time-of-day period."""

    period: str = Field(..., description="Time-of-day period name")
    correction_count: int = Field(..., description="Number of correction boluses")
    under_count: int = Field(
        ..., description="Corrections that left glucose above target"
    )
    over_count: int = Field(
        ..., description="Corrections that dropped glucose below low threshold"
    )
    avg_observed_isf: float = Field(
        ..., description="Average observed ISF (mg/dL drop per unit)"
    )
    avg_glucose_drop: float = Field(
        ..., description="Average glucose drop in mg/dL after correction"
    )


class CorrectionAnalysisResponse(BaseModel):
    """Response schema for a correction factor analysis."""

    model_config = {"from_attributes": True}

    id: uuid.UUID
    period_start: datetime
    period_end: datetime
    total_corrections: int
    under_corrections: int
    over_corrections: int
    avg_observed_isf: float
    time_periods_data: list[TimePeriodData]
    ai_analysis: str
    ai_model: str
    ai_provider: str
    input_tokens: int
    output_tokens: int
    created_at: datetime


class CorrectionAnalysisListResponse(BaseModel):
    """Response schema for listing correction analyses."""

    analyses: list[CorrectionAnalysisResponse]
    total: int


class AnalyzeCorrectionsRequest(BaseModel):
    """Request schema for triggering correction factor analysis."""

    days: int = Field(
        default=7,
        ge=3,
        le=30,
        description="Number of days to analyze (default 7, min 3)",
    )
