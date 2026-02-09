"""Story 6.2: Alerts router.

Provides endpoints for retrieving active alerts and acknowledging them.
"""

import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from src.core.auth import get_current_user
from src.database import get_db
from src.models.user import User
from src.schemas.alert import (
    ActiveAlertsResponse,
    AlertAcknowledgeResponse,
    AlertResponse,
)
from src.services.predictive_alerts import acknowledge_alert, get_active_alerts

router = APIRouter(prefix="/api/alerts", tags=["alerts"])


@router.get("/active", response_model=ActiveAlertsResponse)
async def get_alerts(
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> ActiveAlertsResponse:
    """Get all active (unacknowledged, non-expired) alerts for the current user."""
    alerts = await get_active_alerts(db, user.id)
    return ActiveAlertsResponse(
        alerts=[
            AlertResponse(
                id=str(alert.id),
                alert_type=alert.alert_type.value,
                severity=alert.severity.value,
                current_value=alert.current_value,
                predicted_value=alert.predicted_value,
                prediction_minutes=alert.prediction_minutes,
                iob_value=alert.iob_value,
                message=alert.message,
                trend_rate=alert.trend_rate,
                source=alert.source,
                acknowledged=alert.acknowledged,
                acknowledged_at=alert.acknowledged_at,
                created_at=alert.created_at,
                expires_at=alert.expires_at,
            )
            for alert in alerts
        ],
        count=len(alerts),
    )


@router.patch(
    "/{alert_id}/acknowledge",
    response_model=AlertAcknowledgeResponse,
)
async def acknowledge(
    alert_id: uuid.UUID,
    user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> AlertAcknowledgeResponse:
    """Acknowledge an alert by ID.

    Only the alert's owner can acknowledge it.
    """
    alert = await acknowledge_alert(db, user.id, alert_id)

    if alert is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Alert not found",
        )

    await db.commit()
    await db.refresh(alert)

    return AlertAcknowledgeResponse(
        id=str(alert.id),
        acknowledged=alert.acknowledged,
        acknowledged_at=alert.acknowledged_at,
    )
