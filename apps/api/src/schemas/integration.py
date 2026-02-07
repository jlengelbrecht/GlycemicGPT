"""Story 3.1: Integration schemas.

Pydantic schemas for third-party integration credentials.
"""

from datetime import datetime

from pydantic import BaseModel, EmailStr, Field

from src.models.integration import IntegrationStatus, IntegrationType


class DexcomCredentialsRequest(BaseModel):
    """Request schema for Dexcom Share credentials."""

    username: EmailStr = Field(..., description="Dexcom Share email address")
    password: str = Field(
        ...,
        min_length=1,
        description="Dexcom Share password",
    )


class TandemCredentialsRequest(BaseModel):
    """Request schema for Tandem t:connect credentials."""

    username: EmailStr = Field(..., description="Tandem t:connect email address")
    password: str = Field(
        ...,
        min_length=1,
        description="Tandem t:connect password",
    )
    region: str = Field(
        default="US",
        pattern="^(US|EU)$",
        description="Account region: 'US' or 'EU'",
    )


class IntegrationResponse(BaseModel):
    """Response schema for integration status."""

    model_config = {"from_attributes": True}

    integration_type: IntegrationType
    status: IntegrationStatus
    last_sync_at: datetime | None = None
    last_error: str | None = None
    created_at: datetime
    updated_at: datetime


class IntegrationConnectResponse(BaseModel):
    """Response schema for successful integration connection."""

    message: str = Field(..., description="Success message")
    integration: IntegrationResponse


class IntegrationListResponse(BaseModel):
    """Response schema for listing all integrations."""

    integrations: list[IntegrationResponse]


class IntegrationDisconnectResponse(BaseModel):
    """Response schema for disconnecting an integration."""

    message: str = Field(default="Integration disconnected successfully")
