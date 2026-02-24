"""Treatment safety validator.

Stub implementation for treatment safety validation. All methods
raise NotImplementedError. See CONTRIBUTING.md for the pump control
policy that must be followed before implementing any of these methods.

See __init__.py for important regulatory context.
"""

from src.core.treatment_safety.models import (
    BolusRequest,
    BolusValidationResult,
    SafetyCheckResult,
)

_NOT_IMPLEMENTED_MSG = (
    "Treatment safety validation is not implemented. "
    "See CONTRIBUTING.md for the pump control policy and required "
    "regulatory steps before implementing treatment delivery features."
)


class TreatmentSafetyValidator:
    """Validates treatment requests against safety limits.

    All methods are stubs that raise NotImplementedError. This class
    exists as structural scaffolding to define where treatment safety
    validation would live if pump control is ever implemented.
    """

    def validate_bolus_request(self, request: BolusRequest) -> BolusValidationResult:
        """Run all safety checks against a bolus request.

        Implementation must call every ``_check_*`` method and aggregate
        their ``SafetyCheckResult`` outputs into the returned
        ``BolusValidationResult``. All checks must be evaluated (no
        short-circuit on first pass); a single failing check must fail
        the overall result.

        This method must never trigger or schedule a dosing action.
        Callers are responsible for any subsequent delivery step and
        must surface a human-readable disclaimer to the end user.

        Args:
            request: The bolus request to validate.

        Raises:
            NotImplementedError: Always. Not yet implemented.
        """
        raise NotImplementedError(_NOT_IMPLEMENTED_MSG)

    def _check_max_single_bolus(self, request: BolusRequest) -> SafetyCheckResult:
        """Check that the requested dose does not exceed the max single bolus limit.

        Args:
            request: The bolus request to validate.

        Raises:
            NotImplementedError: Always. Not yet implemented.
        """
        raise NotImplementedError(_NOT_IMPLEMENTED_MSG)

    def _check_max_daily_total(self, request: BolusRequest) -> SafetyCheckResult:
        """Check that delivering this dose would not exceed the max daily total.

        Args:
            request: The bolus request to validate.

        Raises:
            NotImplementedError: Always. Not yet implemented.
        """
        raise NotImplementedError(_NOT_IMPLEMENTED_MSG)

    def _check_cgm_freshness(self, request: BolusRequest) -> SafetyCheckResult:
        """Check that the CGM reading is recent enough to base treatment on.

        Args:
            request: The bolus request to validate.

        Raises:
            NotImplementedError: Always. Not yet implemented.
        """
        raise NotImplementedError(_NOT_IMPLEMENTED_MSG)

    def _check_rate_limit(self, request: BolusRequest) -> SafetyCheckResult:
        """Check that bolus requests are not being made too frequently.

        Args:
            request: The bolus request to validate.

        Raises:
            NotImplementedError: Always. Not yet implemented.
        """
        raise NotImplementedError(_NOT_IMPLEMENTED_MSG)

    def _check_glucose_range(self, request: BolusRequest) -> SafetyCheckResult:
        """Check that the glucose reading is within a safe range for treatment.

        Args:
            request: The bolus request to validate.

        Raises:
            NotImplementedError: Always. Not yet implemented.
        """
        raise NotImplementedError(_NOT_IMPLEMENTED_MSG)
