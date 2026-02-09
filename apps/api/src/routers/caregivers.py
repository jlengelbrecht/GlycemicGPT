"""Stories 8.1 & 8.2: Caregiver invitation, linking, and permissions router.

Endpoints for creating/managing invitations (diabetic users),
accepting invitations (unauthenticated caregivers), and
configuring per-caregiver data access permissions.
"""

import os
import uuid

from fastapi import APIRouter, Depends, HTTPException, Path, Request, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from src.core.auth import CurrentUser, require_caregiver, require_diabetic
from src.core.security import hash_password
from src.database import get_db
from src.models.user import User
from src.schemas.caregiver import (
    AcceptInvitationRequest,
    AcceptInvitationResponse,
    InvitationCreateResponse,
    InvitationDetailResponse,
    InvitationListItem,
    InvitationListResponse,
    LinkedPatientResponse,
    LinkedPatientsListResponse,
)
from src.schemas.caregiver_permissions import (
    CaregiverPermissions,
    LinkedCaregiverItem,
    LinkedCaregiversResponse,
    PermissionsUpdateRequest,
    PermissionsUpdateResponse,
)
from src.services.caregiver import (
    accept_invitation,
    create_invitation,
    get_invitation_by_token,
    get_link_permissions,
    get_linked_caregivers,
    get_linked_patients,
    list_invitations,
    revoke_invitation,
    update_link_permissions,
)

router = APIRouter(prefix="/api/caregivers", tags=["caregivers"])

# Frontend base URL for building invite links
FRONTEND_BASE_URL = os.environ.get("FRONTEND_BASE_URL", "")


def _mask_email(email: str) -> str:
    """Mask an email address for display to unauthenticated users.

    Example: 'patient@example.com' → 'p*****t@example.com'
    """
    local, _, domain = email.partition("@")
    if not domain:
        return "***"
    if len(local) <= 2:
        masked_local = local[0] + "*" * max(len(local) - 1, 1)
    else:
        masked_local = local[0] + "*" * (len(local) - 2) + local[-1]
    return f"{masked_local}@{domain}"


def _build_invite_url(request: Request, token: str) -> str:
    """Build the frontend invite URL from the current request."""
    if FRONTEND_BASE_URL:
        return f"{FRONTEND_BASE_URL.rstrip('/')}/invite/{token}"
    # Fallback: derive from request base URL
    base = str(request.base_url).rstrip("/")
    return f"{base}/invite/{token}"


@router.post(
    "/invitations",
    response_model=InvitationCreateResponse,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(require_diabetic)],
)
async def create_caregiver_invitation(
    request: Request,
    current_user: CurrentUser,
    db: AsyncSession = Depends(get_db),
) -> InvitationCreateResponse:
    """Create a new caregiver invitation.

    Generates a unique token that can be shared with a caregiver.
    Maximum 10 pending invitations per patient.
    """
    try:
        invitation = await create_invitation(db, current_user.id)
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=str(exc),
        ) from exc

    return InvitationCreateResponse(
        id=invitation.id,
        token=invitation.token,
        expires_at=invitation.expires_at,
        invite_url=_build_invite_url(request, invitation.token),
    )


@router.get(
    "/invitations",
    response_model=InvitationListResponse,
    dependencies=[Depends(require_diabetic)],
)
async def list_caregiver_invitations(
    current_user: CurrentUser,
    db: AsyncSession = Depends(get_db),
) -> InvitationListResponse:
    """List all caregiver invitations for the current patient."""
    invitations = await list_invitations(db, current_user.id)

    # Batch-resolve accepted_by emails to avoid N+1 queries
    acceptor_ids = [inv.accepted_by for inv in invitations if inv.accepted_by]
    email_map: dict[uuid.UUID, str] = {}
    if acceptor_ids:
        result = await db.execute(
            select(User.id, User.email).where(User.id.in_(acceptor_ids))
        )
        email_map = {row.id: row.email for row in result.all()}

    items = [
        InvitationListItem(
            id=inv.id,
            status=inv.status,
            created_at=inv.created_at,
            expires_at=inv.expires_at,
            accepted_by_email=email_map.get(inv.accepted_by)
            if inv.accepted_by
            else None,
        )
        for inv in invitations
    ]

    return InvitationListResponse(invitations=items, count=len(items))


@router.delete(
    "/invitations/{invitation_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    dependencies=[Depends(require_diabetic)],
)
async def revoke_caregiver_invitation(
    invitation_id: uuid.UUID,
    current_user: CurrentUser,
    db: AsyncSession = Depends(get_db),
) -> None:
    """Revoke a pending caregiver invitation."""
    try:
        result = await revoke_invitation(db, current_user.id, invitation_id)
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=str(exc),
        ) from exc

    if result is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Invitation not found",
        )


@router.get(
    "/invitations/{token}/details",
    response_model=InvitationDetailResponse,
)
async def get_invitation_details(
    token: str = Path(..., min_length=20, max_length=64),
    db: AsyncSession = Depends(get_db),
) -> InvitationDetailResponse:
    """Get public invitation details for the acceptance page.

    No authentication required — the token acts as authorization.
    Patient email is masked for privacy.
    """
    invitation = await get_invitation_by_token(db, token)

    if invitation is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Invitation not found",
        )

    # Resolve patient email (masked for privacy on public endpoint)
    result = await db.execute(
        select(User.email).where(User.id == invitation.patient_id)
    )
    raw_email = result.scalar_one_or_none() or "Unknown"
    masked = _mask_email(raw_email)

    return InvitationDetailResponse(
        patient_email=masked,
        status=invitation.status,
        expires_at=invitation.expires_at,
    )


@router.post(
    "/accept",
    response_model=AcceptInvitationResponse,
    status_code=status.HTTP_201_CREATED,
)
async def accept_caregiver_invitation(
    data: AcceptInvitationRequest,
    db: AsyncSession = Depends(get_db),
) -> AcceptInvitationResponse:
    """Accept a caregiver invitation.

    Creates a new CAREGIVER account (or links existing caregiver)
    and establishes the caregiver-patient link.
    No authentication required — the token acts as authorization.
    """
    hashed_pw = hash_password(data.password)

    try:
        caregiver, _invitation = await accept_invitation(
            db, data.token, data.email, hashed_pw
        )
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(exc),
        ) from exc

    return AcceptInvitationResponse(
        message="Invitation accepted successfully",
        user_id=caregiver.id,
    )


@router.get(
    "/patients",
    response_model=LinkedPatientsListResponse,
    dependencies=[Depends(require_caregiver)],
)
async def list_linked_patients(
    current_user: CurrentUser,
    db: AsyncSession = Depends(get_db),
) -> LinkedPatientsListResponse:
    """List all patients linked to the current caregiver."""
    links = await get_linked_patients(db, current_user.id)

    patients = [
        LinkedPatientResponse(
            patient_id=link.patient_id,
            patient_email=link.patient.email if link.patient else "Unknown",
            linked_at=link.created_at,
        )
        for link in links
    ]

    return LinkedPatientsListResponse(patients=patients, count=len(patients))


# ── Story 8.2: Caregiver permission endpoints ──


@router.get(
    "/linked",
    response_model=LinkedCaregiversResponse,
    dependencies=[Depends(require_diabetic)],
)
async def list_linked_caregivers_endpoint(
    current_user: CurrentUser,
    db: AsyncSession = Depends(get_db),
) -> LinkedCaregiversResponse:
    """List all caregivers linked to the current patient, with permissions."""
    links = await get_linked_caregivers(db, current_user.id)

    caregivers = [
        LinkedCaregiverItem(
            link_id=link.id,
            caregiver_id=link.caregiver_id,
            caregiver_email=link.caregiver.email if link.caregiver else "Unknown",
            linked_at=link.created_at,
            permissions=CaregiverPermissions(
                can_view_glucose=link.can_view_glucose,
                can_view_history=link.can_view_history,
                can_view_iob=link.can_view_iob,
                can_view_ai_suggestions=link.can_view_ai_suggestions,
                can_receive_alerts=link.can_receive_alerts,
            ),
        )
        for link in links
    ]

    return LinkedCaregiversResponse(caregivers=caregivers, count=len(caregivers))


@router.get(
    "/linked/{link_id}/permissions",
    response_model=PermissionsUpdateResponse,
    dependencies=[Depends(require_diabetic)],
)
async def get_caregiver_permissions_endpoint(
    link_id: uuid.UUID,
    current_user: CurrentUser,
    db: AsyncSession = Depends(get_db),
) -> PermissionsUpdateResponse:
    """Get permissions for a specific caregiver link."""
    link = await get_link_permissions(db, current_user.id, link_id)

    if link is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Caregiver link not found",
        )

    return PermissionsUpdateResponse(
        link_id=link.id,
        permissions=CaregiverPermissions(
            can_view_glucose=link.can_view_glucose,
            can_view_history=link.can_view_history,
            can_view_iob=link.can_view_iob,
            can_view_ai_suggestions=link.can_view_ai_suggestions,
            can_receive_alerts=link.can_receive_alerts,
        ),
    )


@router.patch(
    "/linked/{link_id}/permissions",
    response_model=PermissionsUpdateResponse,
    dependencies=[Depends(require_diabetic)],
)
async def update_caregiver_permissions_endpoint(
    link_id: uuid.UUID,
    data: PermissionsUpdateRequest,
    current_user: CurrentUser,
    db: AsyncSession = Depends(get_db),
) -> PermissionsUpdateResponse:
    """Update permissions for a specific caregiver link.

    Only provided fields are updated; omitted fields remain unchanged.
    Changes take effect immediately.
    """
    updates = data.model_dump(exclude_unset=True)

    if not updates:
        # No fields provided — just return current state
        link = await get_link_permissions(db, current_user.id, link_id)
        if link is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Caregiver link not found",
            )
    else:
        link = await update_link_permissions(db, current_user.id, link_id, **updates)

    if link is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Caregiver link not found",
        )

    return PermissionsUpdateResponse(
        link_id=link.id,
        permissions=CaregiverPermissions(
            can_view_glucose=link.can_view_glucose,
            can_view_history=link.can_view_history,
            can_view_iob=link.can_view_iob,
            can_view_ai_suggestions=link.can_view_ai_suggestions,
            can_receive_alerts=link.can_receive_alerts,
        ),
    )
