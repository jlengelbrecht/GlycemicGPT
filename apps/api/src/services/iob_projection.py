"""Story 3.7: Insulin on Board (IoB) Projection Engine.

Provides projected IoB calculations based on pump-confirmed snapshots
combined with dose-summation for insulin delivered after the snapshot.
Uses decay curves for rapid-acting insulins (Novolog/Humalog).
"""

import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from sqlalchemy import desc, select
from sqlalchemy.ext.asyncio import AsyncSession

from src.models.pump_data import PumpEvent, PumpEventType


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
    is_estimated: bool = False  # True when no pump confirmation exists


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


async def _fetch_insulin_doses(
    db: AsyncSession,
    user_id: uuid.UUID,
    dia_hours: float,
    reference_time: datetime,
) -> list[tuple[datetime, float]]:
    """Fetch all insulin-delivering events within the DIA window.

    Returns bolus and correction events with their timestamps and units.
    Basal events are excluded because their insulin contribution is already
    captured in the pump's IoB snapshot.

    Returns:
        List of (event_timestamp, units) tuples.
    """
    cutoff = reference_time - timedelta(hours=dia_hours)
    result = await db.execute(
        select(PumpEvent.event_timestamp, PumpEvent.units)
        .where(
            PumpEvent.user_id == user_id,
            PumpEvent.event_type.in_(
                [
                    PumpEventType.BOLUS,
                    PumpEventType.CORRECTION,
                ]
            ),
            PumpEvent.units.isnot(None),
            PumpEvent.units > 0,
            PumpEvent.event_timestamp >= cutoff,
            PumpEvent.event_timestamp <= reference_time,
        )
        .order_by(PumpEvent.event_timestamp)
    )
    return [(row[0], row[1]) for row in result.all()]


def _sum_iob_from_doses(
    doses: list[tuple[datetime, float]],
    at_time: datetime,
    dia_hours: float = INSULIN_DIA_HOURS,
) -> float:
    """Compute total IoB at a given time from a list of insulin doses.

    For each dose, applies the decay curve based on elapsed time and sums
    the remaining insulin across all doses.

    Args:
        doses: List of (event_timestamp, units) tuples.
        at_time: Time to compute IoB at.
        dia_hours: Duration of insulin action.

    Returns:
        Total remaining insulin in units.
    """
    total = 0.0
    for event_time, units in doses:
        elapsed = (at_time - event_time).total_seconds() / 3600
        if elapsed < 0:
            continue  # dose is in the future relative to at_time
        remaining = calculate_insulin_remaining(elapsed, dia_hours)
        total += units * remaining
    return total


async def get_iob_projection(
    db: AsyncSession,
    user_id: uuid.UUID,
    dia_hours: float = INSULIN_DIA_HOURS,
) -> IoBProjection | None:
    """Get projected IoB for a user using hybrid dose-summation.

    Uses pump-confirmed IoB as an anchor (captures all historical insulin
    including basal), then adds insulin from bolus/correction doses
    delivered after the confirmation. This prevents the projection from
    ignoring new doses that occurred after the pump's last IoB snapshot.

    Args:
        db: Database session
        user_id: User ID to query
        dia_hours: Duration of insulin action (default 4 hours)

    Returns:
        IoBProjection with confirmed and projected values, or None if no data
    """
    now = datetime.now(UTC)

    # Step 1: Get last pump-confirmed IoB (any age within DIA window)
    last_confirmed_iob, last_confirmed_at = await get_last_iob(
        db, user_id, max_hours=dia_hours
    )

    # Step 2: Fetch all bolus/correction doses within DIA window
    all_doses = await _fetch_insulin_doses(db, user_id, dia_hours, now)

    # No data at all
    if last_confirmed_iob is None and not all_doses:
        return None

    # Step 3: Pre-filter to only post-confirmation doses (avoids re-scanning per call)
    if last_confirmed_at is not None:
        post_doses = [(t, u) for t, u in all_doses if t > last_confirmed_at]
    else:
        post_doses = all_doses

    # Step 4: Compute IoB at now, +30min, +60min
    def _compute_at(at_time: datetime) -> float:
        pump_component = 0.0
        if last_confirmed_iob is not None and last_confirmed_at is not None:
            pump_component = project_iob(
                last_confirmed_iob, last_confirmed_at, at_time, dia_hours
            )
        post_component = _sum_iob_from_doses(post_doses, at_time, dia_hours)
        return max(0.0, pump_component + post_component)

    current_iob = _compute_at(now)
    iob_30 = _compute_at(now + timedelta(minutes=30))
    iob_60 = _compute_at(now + timedelta(minutes=60))

    # Step 5: Determine if this is a fallback (no pump confirmation)
    is_estimated = last_confirmed_at is None
    if is_estimated:
        last_confirmed_iob = round(current_iob, 2)
        last_confirmed_at = now

    # Step 6: Staleness check (based on last pump confirmation)
    elapsed_since = now - last_confirmed_at
    minutes_since = int(elapsed_since.total_seconds() / 60)
    is_stale = minutes_since > 120
    stale_warning = None
    if is_stale:
        stale_warning = "IoB projection may be unreliable - data is over 2 hours old"
    elif is_estimated:
        stale_warning = (
            "IoB estimated from dose history only - no pump confirmation available"
        )

    return IoBProjection(
        confirmed_iob=round(last_confirmed_iob, 2),
        confirmed_at=last_confirmed_at,
        projected_iob=round(current_iob, 2),
        projected_at=now,
        projected_30min=round(iob_30, 2),
        projected_60min=round(iob_60, 2),
        minutes_since_confirmed=minutes_since,
        is_stale=is_stale,
        stale_warning=stale_warning,
        is_estimated=is_estimated,
    )
