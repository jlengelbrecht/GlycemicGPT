"""Story 6.5: Emergency contact service.

CRUD operations for emergency contacts with a per-user limit of 3.
"""

import uuid

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from src.logging_config import get_logger
from src.models.emergency_contact import EmergencyContact
from src.schemas.emergency_contact import (
    EmergencyContactCreate,
    EmergencyContactUpdate,
)

logger = get_logger(__name__)

MAX_CONTACTS_PER_USER = 3


async def list_contacts(
    user_id: uuid.UUID,
    db: AsyncSession,
) -> list[EmergencyContact]:
    """List all emergency contacts for a user, ordered by priority then position."""
    result = await db.execute(
        select(EmergencyContact)
        .where(EmergencyContact.user_id == user_id)
        .order_by(EmergencyContact.priority, EmergencyContact.position)
    )
    return list(result.scalars().all())


async def get_contact(
    user_id: uuid.UUID,
    contact_id: uuid.UUID,
    db: AsyncSession,
) -> EmergencyContact | None:
    """Get a single emergency contact, scoped to the user."""
    result = await db.execute(
        select(EmergencyContact).where(
            EmergencyContact.id == contact_id,
            EmergencyContact.user_id == user_id,
        )
    )
    return result.scalar_one_or_none()


async def create_contact(
    user_id: uuid.UUID,
    data: EmergencyContactCreate,
    db: AsyncSession,
) -> EmergencyContact:
    """Create a new emergency contact.

    Uses SELECT FOR UPDATE to prevent race conditions on the contact limit.

    Raises:
        ValueError: If the user already has MAX_CONTACTS_PER_USER contacts.
        ValueError: If the telegram username already exists for this user.
    """
    # Lock existing rows to prevent concurrent inserts bypassing the limit
    result = await db.execute(
        select(EmergencyContact)
        .where(EmergencyContact.user_id == user_id)
        .with_for_update()
    )
    existing = list(result.scalars().all())

    if len(existing) >= MAX_CONTACTS_PER_USER:
        msg = f"Maximum of {MAX_CONTACTS_PER_USER} emergency contacts allowed"
        raise ValueError(msg)

    # Use max position + 1 to avoid gaps/collisions after deletes
    position = max((c.position for c in existing), default=-1) + 1

    contact = EmergencyContact(
        user_id=user_id,
        name=data.name,
        telegram_username=data.telegram_username,
        priority=data.priority,
        position=position,
    )
    db.add(contact)

    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        msg = "A contact with this Telegram username already exists for this user"
        raise ValueError(msg)  # noqa: B904

    await db.refresh(contact)

    logger.info(
        "Created emergency contact",
        user_id=str(user_id),
        contact_id=str(contact.id),
        telegram=data.telegram_username,
    )

    return contact


async def update_contact(
    user_id: uuid.UUID,
    contact_id: uuid.UUID,
    updates: EmergencyContactUpdate,
    db: AsyncSession,
) -> EmergencyContact | None:
    """Update an emergency contact. Returns None if not found.

    Raises:
        ValueError: If updating telegram_username to a duplicate.
    """
    contact = await get_contact(user_id, contact_id, db)
    if contact is None:
        return None

    update_data = updates.model_dump(exclude_none=True)
    for field, value in update_data.items():
        setattr(contact, field, value)

    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        msg = "A contact with this Telegram username already exists for this user"
        raise ValueError(msg)  # noqa: B904

    await db.refresh(contact)

    logger.info(
        "Updated emergency contact",
        user_id=str(user_id),
        contact_id=str(contact_id),
        fields=list(update_data.keys()),
    )

    return contact


async def delete_contact(
    user_id: uuid.UUID,
    contact_id: uuid.UUID,
    db: AsyncSession,
) -> bool:
    """Delete an emergency contact. Returns True if deleted, False if not found."""
    contact = await get_contact(user_id, contact_id, db)
    if contact is None:
        return False

    await db.delete(contact)
    await db.commit()

    logger.info(
        "Deleted emergency contact",
        user_id=str(user_id),
        contact_id=str(contact_id),
    )

    return True
