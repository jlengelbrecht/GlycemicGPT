"""Story 6.6: Escalation timing configuration service.

Manages user escalation timing with get-or-create pattern.
"""

import uuid

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from src.logging_config import get_logger
from src.models.escalation_config import EscalationConfig
from src.schemas.escalation_config import EscalationConfigUpdate

logger = get_logger(__name__)


async def get_or_create_config(
    user_id: uuid.UUID,
    db: AsyncSession,
) -> EscalationConfig:
    """Get the user's escalation config, creating defaults if none exist.

    Args:
        user_id: User's UUID.
        db: Database session.

    Returns:
        The user's EscalationConfig record.
    """
    result = await db.execute(
        select(EscalationConfig).where(EscalationConfig.user_id == user_id)
    )
    config = result.scalar_one_or_none()

    if config is None:
        config = EscalationConfig(user_id=user_id)
        db.add(config)
        try:
            await db.commit()
        except IntegrityError:
            # Concurrent request already created the row — fetch it
            await db.rollback()
            result = await db.execute(
                select(EscalationConfig).where(EscalationConfig.user_id == user_id)
            )
            config = result.scalar_one()
            return config
        await db.refresh(config)

        logger.info(
            "Created default escalation config",
            user_id=str(user_id),
        )

    return config


async def update_config(
    user_id: uuid.UUID,
    updates: EscalationConfigUpdate,
    db: AsyncSession,
) -> EscalationConfig:
    """Update the user's escalation timing configuration.

    Only fields provided in the request are updated. Validates
    tier ordering against both new and existing values.

    Args:
        user_id: User's UUID.
        updates: Partial update with new timing values.
        db: Database session.

    Returns:
        The updated EscalationConfig record.

    Raises:
        ValueError: If tier ordering is invalid after merge.
    """
    config = await get_or_create_config(user_id, db)

    # Merge new values with existing, keeping existing for unset fields
    new_reminder = (
        updates.reminder_delay_minutes
        if updates.reminder_delay_minutes is not None
        else config.reminder_delay_minutes
    )
    new_primary = (
        updates.primary_contact_delay_minutes
        if updates.primary_contact_delay_minutes is not None
        else config.primary_contact_delay_minutes
    )
    new_all = (
        updates.all_contacts_delay_minutes
        if updates.all_contacts_delay_minutes is not None
        else config.all_contacts_delay_minutes
    )

    # Validate merged ordering
    if new_reminder >= new_primary:
        msg = (
            f"reminder_delay_minutes ({new_reminder}) must be less than "
            f"primary_contact_delay_minutes ({new_primary})"
        )
        raise ValueError(msg)

    if new_primary >= new_all:
        msg = (
            f"primary_contact_delay_minutes ({new_primary}) must be less than "
            f"all_contacts_delay_minutes ({new_all})"
        )
        raise ValueError(msg)

    # Apply updates
    update_data = updates.model_dump(exclude_none=True)
    for field, value in update_data.items():
        setattr(config, field, value)

    await db.commit()
    await db.refresh(config)

    logger.info(
        "Updated escalation config",
        user_id=str(user_id),
        fields=list(update_data.keys()),
    )

    return config
