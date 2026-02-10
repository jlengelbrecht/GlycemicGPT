"""Story 9.1: Target glucose range service.

Manages user target glucose range configuration with get-or-create pattern.
"""

import uuid

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from src.logging_config import get_logger
from src.models.target_glucose_range import TargetGlucoseRange
from src.schemas.target_glucose_range import TargetGlucoseRangeUpdate

logger = get_logger(__name__)


async def get_or_create_range(
    user_id: uuid.UUID,
    db: AsyncSession,
) -> TargetGlucoseRange:
    """Get the user's target glucose range, creating defaults if none exist.

    Args:
        user_id: User's UUID.
        db: Database session.

    Returns:
        The user's TargetGlucoseRange record.
    """
    result = await db.execute(
        select(TargetGlucoseRange).where(TargetGlucoseRange.user_id == user_id)
    )
    target_range = result.scalar_one_or_none()

    if target_range is None:
        target_range = TargetGlucoseRange(user_id=user_id)
        db.add(target_range)
        try:
            await db.commit()
        except IntegrityError:
            await db.rollback()
            result = await db.execute(
                select(TargetGlucoseRange).where(TargetGlucoseRange.user_id == user_id)
            )
            target_range = result.scalar_one()
            return target_range
        await db.refresh(target_range)

        logger.info(
            "Created default target glucose range",
            user_id=str(user_id),
        )

    return target_range


async def update_range(
    user_id: uuid.UUID,
    updates: TargetGlucoseRangeUpdate,
    db: AsyncSession,
) -> TargetGlucoseRange:
    """Update the user's target glucose range.

    Only fields provided in the request are updated. Validates
    that low_target < high_target against both new and existing values.

    Args:
        user_id: User's UUID.
        updates: Partial update with new target values.
        db: Database session.

    Returns:
        The updated TargetGlucoseRange record.

    Raises:
        ValueError: If target ordering is invalid after merge.
    """
    target_range = await get_or_create_range(user_id, db)

    new_low = (
        updates.low_target
        if updates.low_target is not None
        else target_range.low_target
    )
    new_high = (
        updates.high_target
        if updates.high_target is not None
        else target_range.high_target
    )

    if new_low >= new_high:
        msg = f"low_target ({new_low}) must be less than high_target ({new_high})"
        logger.warning(
            "Target glucose range validation failed",
            user_id=str(user_id),
            low_target=new_low,
            high_target=new_high,
        )
        raise ValueError(msg)

    update_data = updates.model_dump(exclude_none=True)
    for field, value in update_data.items():
        setattr(target_range, field, value)

    await db.commit()
    await db.refresh(target_range)

    logger.info(
        "Updated target glucose range",
        user_id=str(user_id),
        fields=list(update_data.keys()),
    )

    return target_range
