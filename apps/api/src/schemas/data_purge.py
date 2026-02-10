"""Story 9.4: Data purge schemas."""

from pydantic import BaseModel, Field


class DataPurgeRequest(BaseModel):
    """Request schema for data purge.

    The confirmation_text must be exactly "DELETE" (case-sensitive)
    to proceed with the purge.
    """

    confirmation_text: str = Field(
        ...,
        max_length=10,
        description="Must be exactly 'DELETE' to confirm the purge.",
    )


class DataPurgeResponse(BaseModel):
    """Response schema for a completed data purge."""

    success: bool
    deleted_records: dict[str, int]
    total_deleted: int
    message: str
