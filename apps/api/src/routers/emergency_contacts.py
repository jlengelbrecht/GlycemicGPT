"""Story 6.5: Emergency contacts router.

CRUD endpoints for managing emergency contacts used in alert escalation.
"""

import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from src.core.auth import get_current_user, require_diabetic_or_admin
from src.database import get_db
from src.models.user import User
from src.schemas.emergency_contact import (
    EmergencyContactCreate,
    EmergencyContactListResponse,
    EmergencyContactResponse,
    EmergencyContactUpdate,
)
from src.services.emergency_contact import (
    create_contact,
    delete_contact,
    list_contacts,
    update_contact,
)

router = APIRouter(
    prefix="/api/settings/emergency-contacts",
    tags=["emergency-contacts"],
)


@router.get(
    "",
    response_model=EmergencyContactListResponse,
    dependencies=[Depends(require_diabetic_or_admin)],
)
async def get_contacts(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> EmergencyContactListResponse:
    """List all emergency contacts for the current user."""
    contacts = await list_contacts(user.id, db)
    return EmergencyContactListResponse(
        contacts=[EmergencyContactResponse.model_validate(c) for c in contacts],
        count=len(contacts),
    )


@router.post(
    "",
    response_model=EmergencyContactResponse,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(require_diabetic_or_admin)],
)
async def add_contact(
    data: EmergencyContactCreate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> EmergencyContactResponse:
    """Create a new emergency contact.

    Maximum 3 contacts per user. Returns 409 if the telegram
    username is already registered for this user.
    """
    try:
        contact = await create_contact(user.id, data, db)
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=str(exc),
        ) from exc

    return EmergencyContactResponse.model_validate(contact)


@router.patch(
    "/{contact_id}",
    response_model=EmergencyContactResponse,
    dependencies=[Depends(require_diabetic_or_admin)],
)
async def edit_contact(
    contact_id: uuid.UUID,
    data: EmergencyContactUpdate,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> EmergencyContactResponse:
    """Update an existing emergency contact."""
    try:
        contact = await update_contact(user.id, contact_id, data, db)
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=str(exc),
        ) from exc

    if contact is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Emergency contact not found",
        )

    return EmergencyContactResponse.model_validate(contact)


@router.delete(
    "/{contact_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    dependencies=[Depends(require_diabetic_or_admin)],
)
async def remove_contact(
    contact_id: uuid.UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> None:
    """Delete an emergency contact."""
    deleted = await delete_contact(user.id, contact_id, db)
    if not deleted:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Emergency contact not found",
        )
