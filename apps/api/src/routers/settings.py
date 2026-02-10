"""Stories 6.1, 6.6, 9.1: Settings router.

Provides endpoints for managing user alert thresholds, escalation timing,
and target glucose range configuration.
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from src.core.auth import get_current_user, require_diabetic_or_admin
from src.database import get_db
from src.models.user import User
from src.schemas.alert_threshold import (
    AlertThresholdDefaults,
    AlertThresholdResponse,
    AlertThresholdUpdate,
)
from src.schemas.escalation_config import (
    EscalationConfigDefaults,
    EscalationConfigResponse,
    EscalationConfigUpdate,
)
from src.schemas.target_glucose_range import (
    TargetGlucoseRangeDefaults,
    TargetGlucoseRangeResponse,
    TargetGlucoseRangeUpdate,
)
from src.services.alert_threshold import get_or_create_thresholds, update_thresholds
from src.services.escalation_config import get_or_create_config, update_config
from src.services.target_glucose_range import get_or_create_range, update_range

router = APIRouter(prefix="/api/settings", tags=["settings"])


@router.get(
    "/alert-thresholds",
    response_model=AlertThresholdResponse,
    dependencies=[Depends(require_diabetic_or_admin)],
)
async def get_alert_thresholds(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> AlertThresholdResponse:
    """Get the current user's alert thresholds.

    Returns defaults if no thresholds have been configured yet.
    """
    thresholds = await get_or_create_thresholds(user.id, db)
    return AlertThresholdResponse.model_validate(thresholds)


@router.patch(
    "/alert-thresholds",
    response_model=AlertThresholdResponse,
    dependencies=[Depends(require_diabetic_or_admin)],
)
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


# ── Escalation timing endpoints ──


@router.get(
    "/escalation-config",
    response_model=EscalationConfigResponse,
    dependencies=[Depends(require_diabetic_or_admin)],
)
async def get_escalation_config(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> EscalationConfigResponse:
    """Get the current user's escalation timing configuration.

    Returns defaults if no configuration has been set yet.
    """
    config = await get_or_create_config(user.id, db)
    return EscalationConfigResponse.model_validate(config)


@router.patch(
    "/escalation-config",
    response_model=EscalationConfigResponse,
    dependencies=[Depends(require_diabetic_or_admin)],
)
async def patch_escalation_config(
    body: EscalationConfigUpdate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> EscalationConfigResponse:
    """Update the current user's escalation timing configuration.

    Only provided fields are updated. Validates that tier ordering
    remains consistent (reminder < primary_contact < all_contacts).
    """
    try:
        config = await update_config(user.id, body, db)
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(e),
        ) from e

    return EscalationConfigResponse.model_validate(config)


@router.get("/escalation-config/defaults", response_model=EscalationConfigDefaults)
async def get_escalation_config_defaults() -> EscalationConfigDefaults:
    """Get the default escalation timing values for reference.

    This endpoint does not require authentication.
    """
    return EscalationConfigDefaults()


# ── Target glucose range endpoints (Story 9.1) ──


@router.get(
    "/target-glucose-range",
    response_model=TargetGlucoseRangeResponse,
    dependencies=[Depends(require_diabetic_or_admin)],
)
async def get_target_glucose_range(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> TargetGlucoseRangeResponse:
    """Get the current user's target glucose range.

    Returns defaults (70-180 mg/dL) if no range has been configured yet.
    """
    target_range = await get_or_create_range(user.id, db)
    return TargetGlucoseRangeResponse.model_validate(target_range)


@router.patch(
    "/target-glucose-range",
    response_model=TargetGlucoseRangeResponse,
    dependencies=[Depends(require_diabetic_or_admin)],
)
async def patch_target_glucose_range(
    body: TargetGlucoseRangeUpdate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> TargetGlucoseRangeResponse:
    """Update the current user's target glucose range.

    Only provided fields are updated. Validates that
    low_target < high_target after merge with existing values.
    """
    try:
        target_range = await update_range(user.id, body, db)
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=str(e),
        ) from e

    return TargetGlucoseRangeResponse.model_validate(target_range)


@router.get(
    "/target-glucose-range/defaults",
    response_model=TargetGlucoseRangeDefaults,
)
async def get_target_glucose_range_defaults() -> TargetGlucoseRangeDefaults:
    """Get the default target glucose range values for reference.

    This endpoint does not require authentication.
    """
    return TargetGlucoseRangeDefaults()
