"""Story 16.11: Alert REST API for mobile apps.

Provides polling fallback and acknowledge endpoints.
"""

import uuid
from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy import and_, select
from sqlalchemy.ext.asyncio import AsyncSession

from src.core.auth import CurrentUser
from src.database import get_db
from src.logging_config import get_logger
from src.models.alert import Alert
from src.models.caregiver_link import CaregiverLink
from src.models.user import UserRole

logger = get_logger(__name__)

router = APIRouter(prefix="/api/v1/alerts", tags=["alerts-mobile"])


class AlertResponse(BaseModel):
    """Alert data for mobile clients."""

    id: str
    alert_type: str
    severity: str
    current_value: float
    predicted_value: float | None
    iob_value: float | None
    message: str
    trend_rate: float | None
    timestamp: str
    patient_name: str | None = None
    acknowledged: bool


def alert_to_dict(a: Alert, patient_name: str | None = None) -> dict:
    """Convert an Alert ORM object to a dict for SSE/API responses."""
    d = {
        "id": str(a.id),
        "alert_type": a.alert_type.value,
        "severity": a.severity.value,
        "current_value": a.current_value,
        "predicted_value": a.predicted_value,
        "iob_value": a.iob_value,
        "message": a.message,
        "trend_rate": a.trend_rate,
        "timestamp": a.created_at.isoformat(),
        "acknowledged": a.acknowledged,
    }
    if patient_name is not None:
        d["patient_name"] = patient_name
    return d


@router.get(
    "/pending",
    response_model=list[AlertResponse],
)
async def get_pending_alerts(
    current_user: CurrentUser,
    db: AsyncSession = Depends(get_db),
) -> list[AlertResponse]:
    """Get unacknowledged alerts (polling fallback).

    For diabetic users: returns their own alerts.
    For caregiver users: returns alerts from patients with can_receive_alerts=True.
    """
    now = datetime.now(UTC)
    alerts_out: list[AlertResponse] = []

    if current_user.role == UserRole.CAREGIVER:
        # Get patients this caregiver can receive alerts for
        links_result = await db.execute(
            select(CaregiverLink.patient_id).where(
                and_(
                    CaregiverLink.caregiver_id == current_user.id,
                    CaregiverLink.can_receive_alerts.is_(True),
                )
            )
        )
        patient_ids = [row[0] for row in links_result.all()]

        if not patient_ids:
            return []

        from src.models.user import User

        for patient_id in patient_ids:
            user_result = await db.execute(
                select(User.email).where(User.id == patient_id)
            )
            patient_email = user_result.scalar_one_or_none() or "Unknown"

            result = await db.execute(
                select(Alert)
                .where(
                    and_(
                        Alert.user_id == patient_id,
                        Alert.acknowledged.is_(False),
                        Alert.expires_at > now,
                    )
                )
                .order_by(Alert.created_at.desc())
                .limit(50)
            )
            for a in result.scalars().all():
                alerts_out.append(
                    AlertResponse(**alert_to_dict(a, patient_name=patient_email))
                )
    else:
        result = await db.execute(
            select(Alert)
            .where(
                and_(
                    Alert.user_id == current_user.id,
                    Alert.acknowledged.is_(False),
                    Alert.expires_at > now,
                )
            )
            .order_by(Alert.created_at.desc())
            .limit(50)
        )
        for a in result.scalars().all():
            alerts_out.append(AlertResponse(**alert_to_dict(a)))

    return alerts_out


@router.post(
    "/{alert_id}/acknowledge",
    status_code=status.HTTP_200_OK,
)
async def acknowledge_alert(
    alert_id: uuid.UUID,
    current_user: CurrentUser,
    db: AsyncSession = Depends(get_db),
) -> dict:
    """Mark an alert as acknowledged."""
    result = await db.execute(select(Alert).where(Alert.id == alert_id))
    alert = result.scalar_one_or_none()

    if alert is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Alert not found",
        )

    # Verify the user owns the alert or is a caregiver for the patient
    if alert.user_id != current_user.id:
        if current_user.role == UserRole.CAREGIVER:
            link_result = await db.execute(
                select(CaregiverLink).where(
                    and_(
                        CaregiverLink.caregiver_id == current_user.id,
                        CaregiverLink.patient_id == alert.user_id,
                        CaregiverLink.can_receive_alerts.is_(True),
                    )
                )
            )
            if link_result.scalar_one_or_none() is None:
                raise HTTPException(
                    status_code=status.HTTP_403_FORBIDDEN,
                    detail="Not authorized to acknowledge this alert",
                )
        else:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Not authorized to acknowledge this alert",
            )

    alert.acknowledged = True
    alert.acknowledged_at = datetime.now(UTC)
    await db.commit()

    logger.info(
        "Alert acknowledged via mobile",
        alert_id=str(alert_id),
        user_id=str(current_user.id),
    )

    return {"status": "acknowledged", "alert_id": str(alert_id)}
