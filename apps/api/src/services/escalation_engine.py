"""Story 6.7: Alert escalation engine.

Automatically escalates unacknowledged URGENT and EMERGENCY alerts
to emergency contacts based on user-configured timing.
"""

import uuid
from dataclasses import dataclass
from datetime import UTC, datetime

from sqlalchemy import and_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from src.logging_config import get_logger
from src.models.alert import Alert, AlertSeverity
from src.models.emergency_contact import ContactPriority, EmergencyContact
from src.models.escalation_config import EscalationConfig
from src.models.escalation_event import (
    EscalationEvent,
    EscalationTier,
    NotificationStatus,
)
from src.services.escalation_config import get_or_create_config

logger = get_logger(__name__)


@dataclass
class EscalationDecision:
    """Decision about whether to escalate an alert."""

    should_escalate: bool
    tier: EscalationTier | None
    reason: str


async def get_unacknowledged_critical_alerts(
    db: AsyncSession,
    user_id: uuid.UUID,
) -> list[Alert]:
    """Get all unacknowledged URGENT or EMERGENCY alerts for a user.

    Args:
        db: Database session.
        user_id: User's UUID.

    Returns:
        List of unacknowledged critical alerts, newest first.
    """
    now = datetime.now(UTC)

    result = await db.execute(
        select(Alert)
        .where(
            and_(
                Alert.user_id == user_id,
                Alert.acknowledged.is_(False),
                Alert.expires_at > now,
                Alert.severity.in_([AlertSeverity.URGENT, AlertSeverity.EMERGENCY]),
            )
        )
        .order_by(Alert.created_at.desc())
    )
    return list(result.scalars().all())


async def get_escalation_events_for_alert(
    db: AsyncSession,
    alert_id: uuid.UUID,
) -> list[EscalationEvent]:
    """Get all escalation events for an alert.

    Args:
        db: Database session.
        alert_id: Alert's UUID.

    Returns:
        List of escalation events, ordered by triggered_at.
    """
    result = await db.execute(
        select(EscalationEvent)
        .where(EscalationEvent.alert_id == alert_id)
        .order_by(EscalationEvent.triggered_at)
    )
    return list(result.scalars().all())


def determine_next_escalation_tier(
    alert: Alert,
    config: EscalationConfig,
    existing_events: list[EscalationEvent],
) -> EscalationDecision:
    """Determine if an alert should escalate and to which tier.

    Args:
        alert: The alert to evaluate.
        config: User's escalation configuration.
        existing_events: Escalation events already triggered for this alert.

    Returns:
        EscalationDecision with tier and reason.
    """
    now = datetime.now(UTC)
    alert_age_minutes = (now - alert.created_at).total_seconds() / 60

    # Build set of tiers already triggered
    triggered_tiers = {event.tier for event in existing_events}

    # Check reminder tier (Tier 1)
    if EscalationTier.REMINDER not in triggered_tiers:
        if alert_age_minutes >= config.reminder_delay_minutes:
            return EscalationDecision(
                should_escalate=True,
                tier=EscalationTier.REMINDER,
                reason=(
                    f"Alert age ({alert_age_minutes:.1f}m) >= "
                    f"reminder delay ({config.reminder_delay_minutes}m)"
                ),
            )
        return EscalationDecision(
            should_escalate=False,
            tier=None,
            reason=(
                f"Alert age ({alert_age_minutes:.1f}m) < "
                f"reminder delay ({config.reminder_delay_minutes}m)"
            ),
        )

    # Check primary contact tier (Tier 2)
    if EscalationTier.PRIMARY_CONTACT not in triggered_tiers:
        if alert_age_minutes >= config.primary_contact_delay_minutes:
            return EscalationDecision(
                should_escalate=True,
                tier=EscalationTier.PRIMARY_CONTACT,
                reason=(
                    f"Alert age ({alert_age_minutes:.1f}m) >= "
                    f"primary delay ({config.primary_contact_delay_minutes}m)"
                ),
            )
        return EscalationDecision(
            should_escalate=False,
            tier=None,
            reason=(
                f"Alert age ({alert_age_minutes:.1f}m) < "
                f"primary delay ({config.primary_contact_delay_minutes}m)"
            ),
        )

    # Check all contacts tier (Tier 3)
    if EscalationTier.ALL_CONTACTS not in triggered_tiers:
        if alert_age_minutes >= config.all_contacts_delay_minutes:
            return EscalationDecision(
                should_escalate=True,
                tier=EscalationTier.ALL_CONTACTS,
                reason=(
                    f"Alert age ({alert_age_minutes:.1f}m) >= "
                    f"all contacts delay ({config.all_contacts_delay_minutes}m)"
                ),
            )
        return EscalationDecision(
            should_escalate=False,
            tier=None,
            reason=(
                f"Alert age ({alert_age_minutes:.1f}m) < "
                f"all contacts delay ({config.all_contacts_delay_minutes}m)"
            ),
        )

    # All tiers already triggered
    return EscalationDecision(
        should_escalate=False,
        tier=None,
        reason="All escalation tiers already triggered",
    )


async def get_contacts_for_tier(
    db: AsyncSession,
    user_id: uuid.UUID,
    tier: EscalationTier,
) -> list[EmergencyContact]:
    """Get emergency contacts to notify for a given tier.

    Args:
        db: Database session.
        user_id: User's UUID.
        tier: Escalation tier.

    Returns:
        List of emergency contacts to notify.
    """
    if tier == EscalationTier.REMINDER:
        # Reminder tier: no contacts (user notification only)
        return []

    if tier == EscalationTier.PRIMARY_CONTACT:
        # Primary tier: only primary contacts
        result = await db.execute(
            select(EmergencyContact)
            .where(
                and_(
                    EmergencyContact.user_id == user_id,
                    EmergencyContact.priority == ContactPriority.PRIMARY,
                )
            )
            .order_by(EmergencyContact.position)
        )
        return list(result.scalars().all())

    # All contacts tier
    result = await db.execute(
        select(EmergencyContact)
        .where(EmergencyContact.user_id == user_id)
        .order_by(EmergencyContact.priority, EmergencyContact.position)
    )
    return list(result.scalars().all())


def build_escalation_message(
    alert: Alert,
    tier: EscalationTier,
    user_email: str,
) -> str:
    """Build the notification message for an escalation tier.

    Args:
        alert: The alert being escalated.
        tier: Escalation tier.
        user_email: User's email (for identification).

    Returns:
        Formatted message string.
    """
    if tier == EscalationTier.REMINDER:
        return (
            f"[REMINDER] You have an unacknowledged "
            f"{alert.severity.value.upper()} alert:\n"
            f"{alert.message}\n"
            f"Current glucose: {alert.current_value:.0f} mg/dL\n"
            f"Please acknowledge this alert if you are okay."
        )

    if tier == EscalationTier.PRIMARY_CONTACT:
        return (
            f"{user_email} has a glucose emergency and has not responded.\n"
            f"Alert: {alert.message}\n"
            f"Current glucose: {alert.current_value:.0f} mg/dL\n"
            f"Severity: {alert.severity.value.upper()}\n"
            f"Please check on them immediately."
        )

    # ALL_CONTACTS
    return (
        f"{user_email} has a glucose emergency and has not responded.\n"
        f"Alert: {alert.message}\n"
        f"Current glucose: {alert.current_value:.0f} mg/dL\n"
        f"Severity: {alert.severity.value.upper()}\n"
        f"Primary contact has not responded. Please check on them immediately."
    )


async def dispatch_notification(
    tier: EscalationTier,
    message: str,
    contacts: list[EmergencyContact],
) -> NotificationStatus:
    """Dispatch notification to contacts.

    This is an abstraction layer. For Story 6.7, notifications are logged.
    Epic 7 will implement actual Telegram sending.

    Args:
        tier: Escalation tier.
        message: Message content.
        contacts: Contacts to notify.

    Returns:
        NotificationStatus indicating success/failure.
    """
    if tier == EscalationTier.REMINDER:
        logger.info(
            "Escalation reminder dispatched (user notification)",
            tier=tier.value,
        )
        return NotificationStatus.SENT

    contact_usernames = [c.telegram_username for c in contacts]
    logger.info(
        "Escalation notification dispatched (Telegram placeholder)",
        tier=tier.value,
        contacts=contact_usernames,
    )
    # TODO (Epic 7): Implement actual Telegram sending
    return NotificationStatus.SENT


async def create_escalation_event(
    db: AsyncSession,
    alert: Alert,
    tier: EscalationTier,
    message: str,
    contacts: list[EmergencyContact],
    status: NotificationStatus,
) -> EscalationEvent | None:
    """Create an escalation event record.

    Args:
        db: Database session.
        alert: The alert being escalated.
        tier: Escalation tier.
        message: Message content sent.
        contacts: Contacts notified.
        status: Notification delivery status.

    Returns:
        Created EscalationEvent or None if duplicate (race condition).
    """
    now = datetime.now(UTC)
    event = EscalationEvent(
        alert_id=alert.id,
        user_id=alert.user_id,
        tier=tier,
        triggered_at=now,
        message_content=message,
        notification_status=status,
        contacts_notified=[str(c.id) for c in contacts],
        created_at=now,
    )

    db.add(event)

    try:
        await db.commit()
        await db.refresh(event)
        return event
    except IntegrityError:
        # Unique constraint violation: another process already escalated this tier
        await db.rollback()
        logger.debug(
            "Escalation event already exists (race condition)",
            alert_id=str(alert.id),
            tier=tier.value,
        )
        return None


async def escalate_alert(
    db: AsyncSession,
    alert: Alert,
    user_email: str,
) -> EscalationEvent | None:
    """Escalate an alert to the next tier if appropriate.

    Args:
        db: Database session.
        alert: The alert to potentially escalate.
        user_email: User's email (for message formatting).

    Returns:
        EscalationEvent if escalation occurred, None otherwise.
    """
    config = await get_or_create_config(alert.user_id, db)
    existing_events = await get_escalation_events_for_alert(db, alert.id)
    decision = determine_next_escalation_tier(alert, config, existing_events)

    if not decision.should_escalate:
        logger.debug(
            "No escalation needed",
            alert_id=str(alert.id),
            reason=decision.reason,
        )
        return None

    tier = decision.tier
    logger.info(
        "Escalating alert",
        alert_id=str(alert.id),
        tier=tier.value,
        reason=decision.reason,
    )

    contacts = await get_contacts_for_tier(db, alert.user_id, tier)
    message = build_escalation_message(alert, tier, user_email)
    status = await dispatch_notification(tier, message, contacts)

    event = await create_escalation_event(db, alert, tier, message, contacts, status)

    if event:
        logger.info(
            "Escalation event created",
            event_id=str(event.id),
            tier=tier.value,
            contacts_count=len(contacts),
        )

    return event


async def process_escalations_for_user(
    db: AsyncSession,
    user_id: uuid.UUID,
    user_email: str,
) -> int:
    """Process all eligible escalations for a single user.

    Args:
        db: Database session.
        user_id: User's UUID.
        user_email: User's email.

    Returns:
        Number of escalations triggered.
    """
    alerts = await get_unacknowledged_critical_alerts(db, user_id)

    if not alerts:
        return 0

    escalation_count = 0
    for alert in alerts:
        event = await escalate_alert(db, alert, user_email)
        if event:
            escalation_count += 1

    return escalation_count
