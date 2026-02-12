"""Story 3.7: Insulin on Board (IoB) Projection Engine.

Provides projected IoB calculations based on last confirmed value and
insulin decay curves for rapid-acting insulins (Novolog/Humalog).
"""

import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from sqlalchemy import desc, select
from sqlalchemy.ext.asyncio import AsyncSession

from src.models.pump_data import PumpEvent


async def get_user_dia(db: AsyncSession, user_id: uuid.UUID) -> float:
    """Get the user's configured DIA, or the default 4.0 hours.

    Args:
        db: Database session
        user_id: User ID to look up

    Returns:
        DIA in hours
    """
    from src.models.insulin_config import InsulinConfig

    result = await db.execute(
        select(InsulinConfig).where(InsulinConfig.user_id == user_id)
    )
    config = result.scalar_one_or_none()
    if config is not None:
        return config.dia_hours
    return INSULIN_DIA_HOURS


@dataclass
class IoBProjection:
    """Projected IoB values at different time points."""

    # Last confirmed IoB from pump
    confirmed_iob: float
    confirmed_at: datetime

    # Current projected IoB (accounting for decay since confirmation)
    projected_iob: float
    projected_at: datetime

    # Future projections
    projected_30min: float
    projected_60min: float

    # Data staleness
    minutes_since_confirmed: int
    is_stale: bool  # True if > 2 hours old
    stale_warning: str | None = None


# Insulin activity profile constants for rapid-acting insulin (Novolog/Humalog)
# Based on exponential decay model with 4-hour duration of insulin action (DIA)
INSULIN_DIA_HOURS = 4.0  # Duration of Insulin Action
INSULIN_PEAK_HOURS = 1.0  # Time to peak activity


def calculate_insulin_remaining(
    elapsed_hours: float, dia_hours: float = INSULIN_DIA_HOURS
) -> float:
    """Calculate fraction of insulin remaining after elapsed time.

    Uses a simplified exponential decay model. For rapid-acting insulin:
    - Peak activity at ~1 hour
    - Most insulin action complete by 4 hours
    - Decay follows approximate curve: remaining = 1 - (t/DIA)^2 for t < DIA

    This is a simplified model. Real insulin curves are more complex but this
    provides a reasonable approximation for projection purposes.

    Args:
        elapsed_hours: Hours since insulin was delivered
        dia_hours: Duration of insulin action (default 4 hours)

    Returns:
        Fraction of insulin activity remaining (0.0 to 1.0)
    """
    if elapsed_hours <= 0:
        return 1.0
    if elapsed_hours >= dia_hours:
        return 0.0

    # Parabolic decay model: steeper at the end
    # This approximates the bilinear model commonly used in loop systems
    t_ratio = elapsed_hours / dia_hours
    remaining = 1.0 - (t_ratio * t_ratio)

    return max(0.0, min(1.0, remaining))


def calculate_iob_activity_curve(
    elapsed_hours: float, dia_hours: float = INSULIN_DIA_HOURS
) -> float:
    """Calculate the Walsh-curve inspired insulin activity at a given time.

    This uses a more accurate bilinear decay model that accounts for:
    - Slow initial absorption
    - Peak activity around 60-75 minutes
    - Gradual tail-off

    Args:
        elapsed_hours: Hours since insulin was delivered
        dia_hours: Duration of insulin action (default 4 hours)

    Returns:
        Fraction of insulin activity remaining (0.0 to 1.0)
    """
    if elapsed_hours <= 0:
        return 1.0
    if elapsed_hours >= dia_hours:
        return 0.0

    # Bilinear model parameters
    peak_time = INSULIN_PEAK_HOURS

    if elapsed_hours < peak_time:
        # Rising phase - rapid absorption
        # Activity decreases more slowly at first
        fraction = elapsed_hours / peak_time
        iob_fraction = 1.0 - (0.2 * fraction)  # Only lose 20% in first hour
    else:
        # Falling phase - exponential-like decay
        remaining_time = dia_hours - elapsed_hours
        decay_duration = dia_hours - peak_time
        # Lose remaining 80% over the next 3 hours
        iob_fraction = 0.8 * (remaining_time / decay_duration)

    return max(0.0, min(1.0, iob_fraction))


def project_iob(
    confirmed_iob: float,
    confirmed_at: datetime,
    projection_time: datetime,
    dia_hours: float = INSULIN_DIA_HOURS,
) -> float:
    """Project IoB at a future time based on decay curve.

    Args:
        confirmed_iob: Last confirmed IoB value in units
        confirmed_at: Timestamp of the confirmed IoB
        projection_time: Time to project IoB to
        dia_hours: Duration of insulin action

    Returns:
        Projected IoB in units
    """
    # Calculate elapsed time since confirmation
    elapsed = projection_time - confirmed_at
    elapsed_hours = elapsed.total_seconds() / 3600

    if elapsed_hours <= 0:
        return confirmed_iob

    # Calculate what fraction of the confirmed IoB would remain
    # We use the simplified decay model here
    remaining_fraction = calculate_insulin_remaining(elapsed_hours, dia_hours)

    return confirmed_iob * remaining_fraction


async def get_last_iob(
    db: AsyncSession,
    user_id: uuid.UUID,
    max_hours: float | None = None,
) -> tuple[float | None, datetime | None]:
    """Get the most recent IoB value for a user.

    Args:
        db: Database session
        user_id: User ID to query
        max_hours: Maximum age of IoB reading to consider.
                   Defaults to the user's configured DIA (up to 8h).

    Returns:
        Tuple of (iob_value, timestamp) or (None, None) if no data
    """
    if max_hours is None:
        max_hours = await get_user_dia(db, user_id)
    cutoff = datetime.now(UTC) - timedelta(hours=max_hours)

    result = await db.execute(
        select(PumpEvent.iob_at_event, PumpEvent.event_timestamp)
        .where(
            PumpEvent.user_id == user_id,
            PumpEvent.iob_at_event.isnot(None),
            PumpEvent.event_timestamp >= cutoff,
        )
        .order_by(desc(PumpEvent.event_timestamp))
        .limit(1)
    )

    row = result.first()
    if row:
        return row[0], row[1]
    return None, None


async def get_iob_projection(
    db: AsyncSession,
    user_id: uuid.UUID,
    dia_hours: float = INSULIN_DIA_HOURS,
) -> IoBProjection | None:
    """Get projected IoB for a user.

    Calculates current projected IoB based on last confirmed value,
    plus projections for 30 and 60 minutes ahead.

    Args:
        db: Database session
        user_id: User ID to query
        dia_hours: Duration of insulin action (default 4 hours)

    Returns:
        IoBProjection with confirmed and projected values, or None if no data
    """
    # Get last known IoB
    confirmed_iob, confirmed_at = await get_last_iob(db, user_id)

    if confirmed_iob is None or confirmed_at is None:
        return None

    now = datetime.now(UTC)

    # Calculate time since confirmation
    elapsed = now - confirmed_at
    minutes_since = int(elapsed.total_seconds() / 60)

    # Check staleness (> 2 hours)
    is_stale = minutes_since > 120
    stale_warning = None
    if is_stale:
        stale_warning = "IoB projection may be unreliable - data is over 2 hours old"

    # Calculate projections
    projected_current = project_iob(confirmed_iob, confirmed_at, now, dia_hours)
    projected_30 = project_iob(
        confirmed_iob, confirmed_at, now + timedelta(minutes=30), dia_hours
    )
    projected_60 = project_iob(
        confirmed_iob, confirmed_at, now + timedelta(minutes=60), dia_hours
    )

    return IoBProjection(
        confirmed_iob=confirmed_iob,
        confirmed_at=confirmed_at,
        projected_iob=round(projected_current, 2),
        projected_at=now,
        projected_30min=round(projected_30, 2),
        projected_60min=round(projected_60, 2),
        minutes_since_confirmed=minutes_since,
        is_stale=is_stale,
        stale_warning=stale_warning,
    )
