"""Story 2.4: System administration schemas.

Pydantic schemas for system health and statistics endpoints.
"""

from pydantic import BaseModel, Field


class SystemHealthResponse(BaseModel):
    """Response schema for system health status."""

    status: str = Field(..., description="Overall system status")
    database: str = Field(..., description="Database connection status")
    redis: str = Field(..., description="Redis connection status")
    version: str = Field(..., description="Application version")


class SystemStatsResponse(BaseModel):
    """Response schema for system statistics."""

    total_users: int = Field(..., description="Total registered users")
    active_sessions: int = Field(..., description="Currently active sessions")
    api_requests_today: int = Field(..., description="API requests in last 24 hours")
