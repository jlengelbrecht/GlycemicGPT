"""Story 6.1: Settings router.

Provides endpoints for managing user alert thresholds.
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from src.core.auth import get_current_user
from src.database import get_db
from src.models.user import User
from src.schemas.alert_threshold import (
    AlertThresholdDefaults,
    AlertThresholdResponse,
    AlertThresholdUpdate,
)
from src.services.alert_threshold import get_or_create_thresholds, update_thresholds

router = APIRouter(prefix="/api/settings", tags=["settings"])


@router.get("/alert-thresholds", response_model=AlertThresholdResponse)
async def get_alert_thresholds(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> AlertThresholdResponse:
    """Get the current user's alert thresholds.

    Returns defaults if no thresholds have been configured yet.
    """
    thresholds = await get_or_create_thresholds(user.id, db)
    return AlertThresholdResponse.model_validate(thresholds)


@router.patch("/alert-thresholds", response_model=AlertThresholdResponse)
async def patch_alert_thresholds(
    body: AlertThresholdUpdate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> AlertThresholdResponse:
    """Update the current user's alert thresholds.

    Only provided fields are updated. Validates that threshold
    ordering remains consistent (urgent_low < low_warning <
    high_warning < urgent_high).
    """
    try:
        thresholds = await update_thresholds(user.id, body, db)
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(e),
        ) from e

    return AlertThresholdResponse.model_validate(thresholds)


@router.get("/alert-thresholds/defaults", response_model=AlertThresholdDefaults)
async def get_alert_threshold_defaults() -> AlertThresholdDefaults:
    """Get the default alert threshold values for reference.

    This endpoint does not require authentication.
    """
    return AlertThresholdDefaults()
