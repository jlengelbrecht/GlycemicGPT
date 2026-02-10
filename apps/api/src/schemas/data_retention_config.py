"""Story 9.3: Data retention configuration schemas."""

import uuid
from datetime import datetime

from pydantic import BaseModel, Field

from src.models.data_retention_config import (
    DEFAULT_ANALYSIS_RETENTION_DAYS,
    DEFAULT_AUDIT_RETENTION_DAYS,
    DEFAULT_GLUCOSE_RETENTION_DAYS,
)


class DataRetentionConfigResponse(BaseModel):
    """Response schema for data retention configuration."""

    model_config = {"from_attributes": True}

    id: uuid.UUID
    glucose_retention_days: int
    analysis_retention_days: int
    audit_retention_days: int
    updated_at: datetime


class DataRetentionConfigUpdate(BaseModel):
    """Request schema for updating data retention configuration.

    All fields are optional -- only provided fields are updated.
    Minimum retention is 30 days; maximum is 3650 days (10 years).
    """

    glucose_retention_days: int | None = Field(
        default=None,
        ge=30,
        le=3650,
        description="Glucose data retention in days (30-3650).",
    )
    analysis_retention_days: int | None = Field(
        default=None,
        ge=30,
        le=3650,
        description="AI analysis retention in days (30-3650).",
    )
    audit_retention_days: int | None = Field(
        default=None,
        ge=30,
        le=3650,
        description="Audit log retention in days (30-3650).",
    )


class DataRetentionConfigDefaults(BaseModel):
    """Default data retention configuration values for reference."""

    glucose_retention_days: int = DEFAULT_GLUCOSE_RETENTION_DAYS
    analysis_retention_days: int = DEFAULT_ANALYSIS_RETENTION_DAYS
    audit_retention_days: int = DEFAULT_AUDIT_RETENTION_DAYS


class StorageUsageResponse(BaseModel):
    """Response schema for storage usage record counts."""

    glucose_records: int
    pump_records: int
    analysis_records: int
    audit_records: int
    total_records: int
