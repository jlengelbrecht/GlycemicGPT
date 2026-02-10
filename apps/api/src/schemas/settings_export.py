"""Story 9.5: Settings export schemas."""

import enum

from pydantic import BaseModel, Field


class ExportType(str, enum.Enum):
    """Export type options."""

    SETTINGS_ONLY = "settings_only"
    ALL_DATA = "all_data"


class SettingsExportRequest(BaseModel):
    """Request schema for settings export."""

    export_type: ExportType = Field(
        ...,
        description="Type of export: 'settings_only' or 'all_data'.",
    )


class SettingsExportResponse(BaseModel):
    """Response schema wrapping the full export payload.

    The export_data field contains the structured JSON export
    with metadata, settings, and optionally all user data.
    """

    export_data: dict
