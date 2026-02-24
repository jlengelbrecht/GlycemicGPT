"""Treatment safety enums.

Structural scaffolding for treatment safety validation types.
See __init__.py for important regulatory context.
"""

from enum import Enum


class SafetyCheckType(str, Enum):
    """Types of safety checks applied to treatment requests."""

    max_single_bolus = "max_single_bolus"
    max_daily_total = "max_daily_total"
    cgm_freshness = "cgm_freshness"
    rate_limit = "rate_limit"
    glucose_range_check = "glucose_range_check"
    user_confirmation_required = "user_confirmation_required"
    biometric_required = "biometric_required"


class BolusSource(str, Enum):
    """Origin of a bolus request.

    The ``automated`` member exists to represent closed-loop pump modes.
    Any bolus with source ``automated`` MUST pass
    ``SafetyCheckType.user_confirmation_required`` or
    ``SafetyCheckType.biometric_required`` before being approved.
    This invariant must be enforced structurally in the validator,
    not left to callers.
    """

    manual = "manual"
    ai_suggested = "ai_suggested"
    automated = "automated"


class ValidationStatus(str, Enum):
    """Outcome of a bolus validation check."""

    approved = "approved"
    rejected = "rejected"
    pending_confirmation = "pending_confirmation"
