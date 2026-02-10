"""Story 9.3: Data retention configuration service.

Manages user data retention settings with get-or-create pattern,
and provides the background enforcement job that prunes expired data.
"""

import uuid
from datetime import UTC, datetime, timedelta

from sqlalchemy import delete, func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from src.logging_config import get_logger
from src.models.alert import Alert
from src.models.correction_analysis import CorrectionAnalysis
from src.models.daily_brief import DailyBrief
from src.models.data_retention_config import DataRetentionConfig
from src.models.escalation_event import EscalationEvent
from src.models.glucose import GlucoseReading
from src.models.meal_analysis import MealAnalysis
from src.models.pump_data import PumpEvent
from src.models.safety_log import SafetyLog
from src.models.suggestion_response import SuggestionResponse
from src.schemas.data_retention_config import DataRetentionConfigUpdate

logger = get_logger(__name__)


async def get_or_create_config(
    user_id: uuid.UUID,
    db: AsyncSession,
) -> DataRetentionConfig:
    """Get the user's data retention config, creating defaults if none exist.

    Args:
        user_id: User's UUID.
        db: Database session.

    Returns:
        The user's DataRetentionConfig record.
    """
    result = await db.execute(
        select(DataRetentionConfig).where(DataRetentionConfig.user_id == user_id)
    )
    config = result.scalar_one_or_none()

    if config is None:
        config = DataRetentionConfig(user_id=user_id)
        db.add(config)
        try:
            await db.commit()
        except IntegrityError:
            await db.rollback()
            result = await db.execute(
                select(DataRetentionConfig).where(
                    DataRetentionConfig.user_id == user_id
                )
            )
            config = result.scalar_one()
            return config
        await db.refresh(config)

        logger.info(
            "Created default data retention config",
            user_id=str(user_id),
        )

    return config


async def update_config(
    user_id: uuid.UUID,
    updates: DataRetentionConfigUpdate,
    db: AsyncSession,
) -> DataRetentionConfig:
    """Update the user's data retention configuration.

    Only fields provided in the request are updated.

    Args:
        user_id: User's UUID.
        updates: Partial update with new retention values.
        db: Database session.

    Returns:
        The updated DataRetentionConfig record.
    """
    config = await get_or_create_config(user_id, db)

    update_data = updates.model_dump(exclude_none=True)
    for field, value in update_data.items():
        setattr(config, field, value)

    await db.commit()
    await db.refresh(config)

    logger.info(
        "Updated data retention config",
        user_id=str(user_id),
        fields=list(update_data.keys()),
    )

    return config


async def get_storage_usage(
    user_id: uuid.UUID,
    db: AsyncSession,
) -> dict[str, int]:
    """Get the count of records in each data category for a user.

    Returns a dict with record counts for display purposes.

    Args:
        user_id: User's UUID.
        db: Database session.

    Returns:
        Dict with keys: glucose_records, pump_records, analysis_records,
        audit_records, total_records.
    """
    glucose_count = (
        await db.execute(select(func.count()).where(GlucoseReading.user_id == user_id))
    ).scalar_one()

    pump_count = (
        await db.execute(select(func.count()).where(PumpEvent.user_id == user_id))
    ).scalar_one()

    brief_count = (
        await db.execute(select(func.count()).where(DailyBrief.user_id == user_id))
    ).scalar_one()

    meal_count = (
        await db.execute(select(func.count()).where(MealAnalysis.user_id == user_id))
    ).scalar_one()

    correction_count = (
        await db.execute(
            select(func.count()).where(CorrectionAnalysis.user_id == user_id)
        )
    ).scalar_one()

    suggestion_count = (
        await db.execute(
            select(func.count()).where(SuggestionResponse.user_id == user_id)
        )
    ).scalar_one()

    safety_count = (
        await db.execute(select(func.count()).where(SafetyLog.user_id == user_id))
    ).scalar_one()

    alert_count = (
        await db.execute(select(func.count()).where(Alert.user_id == user_id))
    ).scalar_one()

    escalation_count = (
        await db.execute(select(func.count()).where(EscalationEvent.user_id == user_id))
    ).scalar_one()

    analysis_total = brief_count + meal_count + correction_count + suggestion_count
    audit_total = safety_count + alert_count + escalation_count
    glucose_total = glucose_count + pump_count

    return {
        "glucose_records": glucose_count,
        "pump_records": pump_count,
        "analysis_records": analysis_total,
        "audit_records": audit_total,
        "total_records": glucose_total + analysis_total + audit_total,
    }


async def enforce_retention_for_user(
    user_id: uuid.UUID,
    config: DataRetentionConfig,
    db: AsyncSession,
) -> dict[str, int]:
    """Delete records older than the configured retention period for a user.

    Args:
        user_id: User's UUID.
        config: The user's data retention configuration.
        db: Database session.

    Returns:
        Dict with count of deleted records per category.
    """
    now = datetime.now(UTC)
    deleted = {}

    # Glucose data: GlucoseReading and PumpEvent
    glucose_cutoff = now - timedelta(days=config.glucose_retention_days)

    result = await db.execute(
        delete(GlucoseReading).where(
            GlucoseReading.user_id == user_id,
            GlucoseReading.reading_timestamp < glucose_cutoff,
        )
    )
    deleted["glucose_readings"] = result.rowcount

    result = await db.execute(
        delete(PumpEvent).where(
            PumpEvent.user_id == user_id,
            PumpEvent.event_timestamp < glucose_cutoff,
        )
    )
    deleted["pump_events"] = result.rowcount

    # Analysis data: DailyBrief, MealAnalysis, CorrectionAnalysis, SuggestionResponse
    analysis_cutoff = now - timedelta(days=config.analysis_retention_days)

    result = await db.execute(
        delete(DailyBrief).where(
            DailyBrief.user_id == user_id,
            DailyBrief.created_at < analysis_cutoff,
        )
    )
    deleted["daily_briefs"] = result.rowcount

    result = await db.execute(
        delete(MealAnalysis).where(
            MealAnalysis.user_id == user_id,
            MealAnalysis.created_at < analysis_cutoff,
        )
    )
    deleted["meal_analyses"] = result.rowcount

    result = await db.execute(
        delete(CorrectionAnalysis).where(
            CorrectionAnalysis.user_id == user_id,
            CorrectionAnalysis.created_at < analysis_cutoff,
        )
    )
    deleted["correction_analyses"] = result.rowcount

    result = await db.execute(
        delete(SuggestionResponse).where(
            SuggestionResponse.user_id == user_id,
            SuggestionResponse.created_at < analysis_cutoff,
        )
    )
    deleted["suggestion_responses"] = result.rowcount

    # Audit data: SafetyLog, EscalationEvent, Alert
    # EscalationEvent must be deleted before Alert due to FK cascade
    # (escalation_events.alert_id -> alerts.id ON DELETE CASCADE)
    audit_cutoff = now - timedelta(days=config.audit_retention_days)

    result = await db.execute(
        delete(SafetyLog).where(
            SafetyLog.user_id == user_id,
            SafetyLog.created_at < audit_cutoff,
        )
    )
    deleted["safety_logs"] = result.rowcount

    result = await db.execute(
        delete(EscalationEvent).where(
            EscalationEvent.user_id == user_id,
            EscalationEvent.created_at < audit_cutoff,
        )
    )
    deleted["escalation_events"] = result.rowcount

    result = await db.execute(
        delete(Alert).where(
            Alert.user_id == user_id,
            Alert.created_at < audit_cutoff,
        )
    )
    deleted["alerts"] = result.rowcount

    await db.commit()

    total = sum(deleted.values())
    if total > 0:
        logger.info(
            "Enforced data retention policy",
            user_id=str(user_id),
            deleted=deleted,
            total_deleted=total,
        )

    return deleted
