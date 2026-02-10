"""Story 6.7: Escalation router.

Provides endpoints for viewing escalation history.
"""

import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from src.core.auth import get_current_user, require_diabetic_or_admin
from src.database import get_db
from src.models.alert import Alert
from src.models.user import User
from src.schemas.escalation_event import (
    EscalationEventResponse,
    EscalationTimelineResponse,
)
from src.services.escalation_engine import get_escalation_events_for_alert

router = APIRouter(prefix="/api/escalation", tags=["escalation"])


@router.get(
    "/alerts/{alert_id}/timeline",
    response_model=EscalationTimelineResponse,
    dependencies=[Depends(require_diabetic_or_admin)],
)
async def get_alert_escalation_timeline(
    alert_id: uuid.UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> EscalationTimelineResponse:
    """Get escalation timeline for a specific alert.

    Returns all escalation events for the alert, showing the progression
    through escalation tiers (reminder, primary contact, all contacts).

    Only the alert's owner can view the escalation timeline.
    """
    result = await db.execute(
        select(Alert).where(Alert.id == alert_id, Alert.user_id == user.id)
    )
    alert = result.scalar_one_or_none()

    if alert is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Alert not found",
        )

    events = await get_escalation_events_for_alert(db, alert_id)

    return EscalationTimelineResponse(
        alert_id=alert_id,
        events=[
            EscalationEventResponse(
                id=event.id,
                alert_id=event.alert_id,
                tier=event.tier.value,
                triggered_at=event.triggered_at,
                message_content=event.message_content,
                notification_status=event.notification_status.value,
                contacts_notified=event.contacts_notified,
                created_at=event.created_at,
            )
            for event in events
        ],
        count=len(events),
    )
