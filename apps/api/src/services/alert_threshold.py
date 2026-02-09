"""Story 6.1: Alert threshold service.

Manages user alert threshold configuration with get-or-create pattern.
"""

import uuid

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from src.logging_config import get_logger
from src.models.alert_threshold import AlertThreshold
from src.schemas.alert_threshold import AlertThresholdUpdate

logger = get_logger(__name__)


async def get_or_create_thresholds(
    user_id: uuid.UUID,
    db: AsyncSession,
) -> AlertThreshold:
    """Get the user's alert thresholds, creating defaults if none exist.

    Args:
        user_id: User's UUID.
        db: Database session.

    Returns:
        The user's AlertThreshold record.
    """
    result = await db.execute(
        select(AlertThreshold).where(AlertThreshold.user_id == user_id)
    )
    thresholds = result.scalar_one_or_none()

    if thresholds is None:
        thresholds = AlertThreshold(user_id=user_id)
        db.add(thresholds)
        try:
            await db.commit()
        except IntegrityError:
            # Concurrent request already created the row — fetch it
            await db.rollback()
            result = await db.execute(
                select(AlertThreshold).where(AlertThreshold.user_id == user_id)
            )
            thresholds = result.scalar_one()
            return thresholds
        await db.refresh(thresholds)

        logger.info(
            "Created default alert thresholds",
            user_id=str(user_id),
        )

    return thresholds


async def update_thresholds(
    user_id: uuid.UUID,
    updates: AlertThresholdUpdate,
    db: AsyncSession,
) -> AlertThreshold:
    """Update the user's alert thresholds.

    Only fields provided in the request are updated. Validates
    threshold ordering against both new and existing values.

    Args:
        user_id: User's UUID.
        updates: Partial update with new threshold values.
        db: Database session.

    Returns:
        The updated AlertThreshold record.

    Raises:
        ValueError: If threshold ordering is invalid after merge.
    """
    thresholds = await get_or_create_thresholds(user_id, db)

    # Merge new values with existing, keeping existing for unset fields
    new_urgent_low = (
        updates.urgent_low if updates.urgent_low is not None else thresholds.urgent_low
    )
    new_low_warning = (
        updates.low_warning
        if updates.low_warning is not None
        else thresholds.low_warning
    )
    new_high_warning = (
        updates.high_warning
        if updates.high_warning is not None
        else thresholds.high_warning
    )
    new_urgent_high = (
        updates.urgent_high
        if updates.urgent_high is not None
        else thresholds.urgent_high
    )

    # Validate merged ordering
    if new_urgent_low >= new_low_warning:
        msg = (
            f"urgent_low ({new_urgent_low}) must be less than "
            f"low_warning ({new_low_warning})"
        )
        raise ValueError(msg)

    if new_high_warning >= new_urgent_high:
        msg = (
            f"high_warning ({new_high_warning}) must be less than "
            f"urgent_high ({new_urgent_high})"
        )
        raise ValueError(msg)

    if new_low_warning >= new_high_warning:
        msg = (
            f"low_warning ({new_low_warning}) must be less than "
            f"high_warning ({new_high_warning})"
        )
        raise ValueError(msg)

    # Apply updates
    update_data = updates.model_dump(exclude_none=True)
    for field, value in update_data.items():
        setattr(thresholds, field, value)

    await db.commit()
    await db.refresh(thresholds)

    logger.info(
        "Updated alert thresholds",
        user_id=str(user_id),
        fields=list(update_data.keys()),
    )

    return thresholds
