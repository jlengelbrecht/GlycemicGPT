"""Treatment safety validation scaffolding.

This module provides structural foundation for where treatment safety
validation would live if pump control features are ever implemented.
Currently, all classes and methods are stubs that raise NotImplementedError.

IMPORTANT: This is scaffolding only. No functional treatment validation
exists in this codebase. Before implementing any pump control features:

1. Review MEDICAL-DISCLAIMER.md for regulatory context
2. Review CONTRIBUTING.md for the pump control policy
3. Obtain legal counsel regarding FDA/regulatory requirements
4. All safety limits are enforced at the platform level -- see
   src.schemas.safety_limits for the active safety limit
   configuration used by the monitoring system.
"""

from src.core.treatment_safety.enums import (
    BolusSource,
    SafetyCheckType,
    ValidationStatus,
)
from src.core.treatment_safety.models import (
    BolusRequest,
    BolusValidationResult,
    SafetyCheckResult,
)
from src.core.treatment_safety.validator import TreatmentSafetyValidator

__all__ = [
    "BolusRequest",
    "BolusSource",
    "BolusValidationResult",
    "SafetyCheckResult",
    "SafetyCheckType",
    "TreatmentSafetyValidator",
    "ValidationStatus",
]
