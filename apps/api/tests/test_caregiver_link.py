"""Story 7.6: Tests for CaregiverLink model."""

import uuid

from src.models.caregiver_link import CaregiverLink


class TestCaregiverLinkModel:
    """Tests for the CaregiverLink model."""

    def test_repr(self):
        caregiver_id = uuid.uuid4()
        patient_id = uuid.uuid4()
        link = CaregiverLink(
            caregiver_id=caregiver_id,
            patient_id=patient_id,
        )
        repr_str = repr(link)
        assert str(caregiver_id) in repr_str
        assert str(patient_id) in repr_str
        assert "CaregiverLink" in repr_str

    def test_tablename(self):
        assert CaregiverLink.__tablename__ == "caregiver_links"

    def test_unique_constraint_exists(self):
        """The model has a unique constraint on (caregiver_id, patient_id)."""
        constraints = CaregiverLink.__table_args__
        assert any(
            getattr(c, "name", None) == "uq_caregiver_patient"
            for c in constraints
            if hasattr(c, "name")
        )

    def test_self_link_check_constraint_exists(self):
        """The model has a check constraint preventing self-linking."""
        constraints = CaregiverLink.__table_args__
        assert any(
            getattr(c, "name", None) == "ck_no_self_link"
            for c in constraints
            if hasattr(c, "name")
        )
