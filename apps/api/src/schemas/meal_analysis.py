"""Story 5.4: Meal pattern analysis schemas.

Request and response schemas for post-meal pattern recognition
and carb ratio suggestions.
"""

import uuid
from datetime import datetime

from pydantic import BaseModel, Field


class MealPeriodData(BaseModel):
    """Metrics for a single meal period (breakfast, lunch, dinner, snack)."""

    period: str = Field(..., description="Meal period name")
    bolus_count: int = Field(..., description="Number of meal boluses")
    spike_count: int = Field(..., description="Number of post-meal spikes >180 mg/dL")
    avg_peak_glucose: float = Field(
        ..., description="Average peak glucose within 2hr post-bolus"
    )
    avg_2hr_glucose: float = Field(..., description="Average glucose at 2hr post-bolus")


class MealAnalysisResponse(BaseModel):
    """Response schema for a meal pattern analysis."""

    model_config = {"from_attributes": True}

    id: uuid.UUID
    period_start: datetime
    period_end: datetime
    total_boluses: int
    total_spikes: int
    avg_post_meal_peak: float
    meal_periods_data: list[MealPeriodData]
    ai_analysis: str
    ai_model: str
    ai_provider: str
    input_tokens: int
    output_tokens: int
    created_at: datetime


class MealAnalysisListResponse(BaseModel):
    """Response schema for listing meal analyses."""

    analyses: list[MealAnalysisResponse]
    total: int


class AnalyzeMealsRequest(BaseModel):
    """Request schema for triggering meal pattern analysis."""

    days: int = Field(
        default=7,
        ge=3,
        le=30,
        description="Number of days to analyze (default 7, min 3)",
    )
