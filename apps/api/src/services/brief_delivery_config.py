"""Story 9.2: Brief delivery configuration service.

Manages user brief delivery preferences with get-or-create pattern.
"""

import uuid

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from src.logging_config import get_logger
from src.models.brief_delivery_config import BriefDeliveryConfig
from src.schemas.brief_delivery_config import BriefDeliveryConfigUpdate

logger = get_logger(__name__)


async def get_or_create_config(
    user_id: uuid.UUID,
    db: AsyncSession,
) -> BriefDeliveryConfig:
    """Get the user's brief delivery config, creating defaults if none exist.

    Args:
        user_id: User's UUID.
        db: Database session.

    Returns:
        The user's BriefDeliveryConfig record.
    """
    result = await db.execute(
        select(BriefDeliveryConfig).where(BriefDeliveryConfig.user_id == user_id)
    )
    config = result.scalar_one_or_none()

    if config is None:
        config = BriefDeliveryConfig(user_id=user_id)
        db.add(config)
        try:
            await db.commit()
        except IntegrityError:
            await db.rollback()
            result = await db.execute(
                select(BriefDeliveryConfig).where(
                    BriefDeliveryConfig.user_id == user_id
                )
            )
            config = result.scalar_one()
            return config
        await db.refresh(config)

        logger.info(
            "Created default brief delivery config",
            user_id=str(user_id),
        )

    return config


async def update_config(
    user_id: uuid.UUID,
    updates: BriefDeliveryConfigUpdate,
    db: AsyncSession,
) -> BriefDeliveryConfig:
    """Update the user's brief delivery configuration.

    Only fields provided in the request are updated.

    Args:
        user_id: User's UUID.
        updates: Partial update with new config values.
        db: Database session.

    Returns:
        The updated BriefDeliveryConfig record.
    """
    config = await get_or_create_config(user_id, db)

    update_data = updates.model_dump(exclude_none=True)
    for field, value in update_data.items():
        setattr(config, field, value)

    await db.commit()
    await db.refresh(config)

    logger.info(
        "Updated brief delivery config",
        user_id=str(user_id),
        fields=list(update_data.keys()),
    )

    return config
