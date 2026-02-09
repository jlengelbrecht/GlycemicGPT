"""Story 8.2: Tests for caregiver data access permissions."""

import uuid
from datetime import UTC, datetime
from unittest.mock import AsyncMock, MagicMock

import pytest

from src.models.caregiver_link import CaregiverLink
from src.models.user import User, UserRole
from src.services.caregiver import (
    PERMISSION_FIELDS,
    check_caregiver_permission,
    get_link_permissions,
    get_linked_caregivers,
    update_link_permissions,
)

# ── Helpers ──


def make_link(
    patient_id: uuid.UUID | None = None,
    caregiver_id: uuid.UUID | None = None,
    **overrides: bool,
) -> MagicMock:
    """Create a mock CaregiverLink with permission defaults."""
    link = MagicMock(spec=CaregiverLink)
    link.id = uuid.uuid4()
    link.patient_id = patient_id or uuid.uuid4()
    link.caregiver_id = caregiver_id or uuid.uuid4()
    link.created_at = datetime.now(UTC)
    link.updated_at = datetime.now(UTC)
    # Permission defaults
    link.can_view_glucose = overrides.get("can_view_glucose", True)
    link.can_view_history = overrides.get("can_view_history", True)
    link.can_view_iob = overrides.get("can_view_iob", True)
    link.can_view_ai_suggestions = overrides.get("can_view_ai_suggestions", False)
    link.can_receive_alerts = overrides.get("can_receive_alerts", True)
    # Mock caregiver relationship
    caregiver = MagicMock(spec=User)
    caregiver.id = link.caregiver_id
    caregiver.email = "caregiver@example.com"
    caregiver.role = UserRole.CAREGIVER
    link.caregiver = caregiver
    return link


# ── TestCaregiverLinkPermissionDefaults ──


class TestCaregiverLinkPermissionDefaults:
    """Tests for CaregiverLink permission column defaults."""

    def test_permission_fields_defined(self):
        """All 5 permission fields are in the constant set."""
        expected = {
            "can_view_glucose",
            "can_view_history",
            "can_view_iob",
            "can_view_ai_suggestions",
            "can_receive_alerts",
        }
        assert expected == PERMISSION_FIELDS

    def test_model_has_permission_columns(self):
        """CaregiverLink model has all permission columns."""
        link = CaregiverLink(
            patient_id=uuid.uuid4(),
            caregiver_id=uuid.uuid4(),
        )
        # These should not raise
        assert hasattr(link, "can_view_glucose")
        assert hasattr(link, "can_view_history")
        assert hasattr(link, "can_view_iob")
        assert hasattr(link, "can_view_ai_suggestions")
        assert hasattr(link, "can_receive_alerts")


# ── TestGetLinkedCaregivers ──


class TestGetLinkedCaregivers:
    """Tests for get_linked_caregivers service function."""

    @pytest.mark.asyncio
    async def test_returns_linked_caregivers(self):
        """Returns CaregiverLinks for a patient."""
        link = make_link()

        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = [link]

        db = AsyncMock()
        db.execute.return_value = mock_result

        result = await get_linked_caregivers(db, link.patient_id)
        assert len(result) == 1
        assert result[0] is link

    @pytest.mark.asyncio
    async def test_returns_empty_list(self):
        """Returns empty list when no caregivers linked."""
        mock_result = MagicMock()
        mock_result.scalars.return_value.all.return_value = []

        db = AsyncMock()
        db.execute.return_value = mock_result

        result = await get_linked_caregivers(db, uuid.uuid4())
        assert result == []


# ── TestGetLinkPermissions ──


class TestGetLinkPermissions:
    """Tests for get_link_permissions service function."""

    @pytest.mark.asyncio
    async def test_returns_link(self):
        """Returns the CaregiverLink when found and owned."""
        patient_id = uuid.uuid4()
        link = make_link(patient_id=patient_id)

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = link

        db = AsyncMock()
        db.execute.return_value = mock_result

        result = await get_link_permissions(db, patient_id, link.id)
        assert result is link

    @pytest.mark.asyncio
    async def test_returns_none_when_not_found(self):
        """Returns None for non-existent link."""
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = None

        db = AsyncMock()
        db.execute.return_value = mock_result

        result = await get_link_permissions(db, uuid.uuid4(), uuid.uuid4())
        assert result is None


# ── TestUpdateLinkPermissions ──


class TestUpdateLinkPermissions:
    """Tests for update_link_permissions service function."""

    @pytest.mark.asyncio
    async def test_full_update(self):
        """Updates all permission fields."""
        patient_id = uuid.uuid4()
        link = make_link(patient_id=patient_id)

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = link

        db = AsyncMock()
        db.execute.return_value = mock_result

        result = await update_link_permissions(
            db,
            patient_id,
            link.id,
            can_view_glucose=False,
            can_view_history=False,
            can_view_iob=False,
            can_view_ai_suggestions=True,
            can_receive_alerts=False,
        )

        assert result is link
        assert link.can_view_glucose is False
        assert link.can_view_ai_suggestions is True
        db.commit.assert_called_once()

    @pytest.mark.asyncio
    async def test_partial_update(self):
        """Only updates provided fields."""
        patient_id = uuid.uuid4()
        link = make_link(patient_id=patient_id)

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = link

        db = AsyncMock()
        db.execute.return_value = mock_result

        result = await update_link_permissions(
            db,
            patient_id,
            link.id,
            can_view_ai_suggestions=True,
        )

        assert result is link
        assert link.can_view_ai_suggestions is True
        # Other fields should remain at their default values
        db.commit.assert_called_once()

    @pytest.mark.asyncio
    async def test_no_op_skips_commit(self):
        """Skips commit when no valid fields provided."""
        patient_id = uuid.uuid4()
        link = make_link(patient_id=patient_id)

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = link

        db = AsyncMock()
        db.execute.return_value = mock_result

        result = await update_link_permissions(
            db,
            patient_id,
            link.id,
            invalid_field=True,
        )

        assert result is link
        db.commit.assert_not_called()

    @pytest.mark.asyncio
    async def test_not_found_returns_none(self):
        """Returns None when link not found."""
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = None

        db = AsyncMock()
        db.execute.return_value = mock_result

        result = await update_link_permissions(
            db,
            uuid.uuid4(),
            uuid.uuid4(),
            can_view_glucose=False,
        )

        assert result is None

    @pytest.mark.asyncio
    async def test_ignores_none_values(self):
        """Ignores fields set to None (from exclude_unset)."""
        patient_id = uuid.uuid4()
        link = make_link(patient_id=patient_id)

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = link

        db = AsyncMock()
        db.execute.return_value = mock_result

        result = await update_link_permissions(
            db,
            patient_id,
            link.id,
            can_view_glucose=None,
            can_view_ai_suggestions=True,
        )

        assert result is link
        # Only can_view_ai_suggestions should trigger commit
        db.commit.assert_called_once()


# ── TestCheckCaregiverPermission ──


class TestCheckCaregiverPermission:
    """Tests for check_caregiver_permission service function."""

    @pytest.mark.asyncio
    async def test_permission_granted(self):
        """Returns True when permission is enabled."""
        link = make_link(can_view_glucose=True)

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = link

        db = AsyncMock()
        db.execute.return_value = mock_result

        result = await check_caregiver_permission(
            db, link.caregiver_id, link.patient_id, "can_view_glucose"
        )
        assert result is True

    @pytest.mark.asyncio
    async def test_permission_denied(self):
        """Returns False when permission is disabled."""
        link = make_link(can_view_ai_suggestions=False)

        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = link

        db = AsyncMock()
        db.execute.return_value = mock_result

        result = await check_caregiver_permission(
            db, link.caregiver_id, link.patient_id, "can_view_ai_suggestions"
        )
        assert result is False

    @pytest.mark.asyncio
    async def test_no_link_returns_false(self):
        """Returns False when no CaregiverLink exists."""
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = None

        db = AsyncMock()
        db.execute.return_value = mock_result

        result = await check_caregiver_permission(
            db, uuid.uuid4(), uuid.uuid4(), "can_view_glucose"
        )
        assert result is False

    @pytest.mark.asyncio
    async def test_invalid_permission_returns_false(self):
        """Returns False for unknown permission field names."""
        db = AsyncMock()

        result = await check_caregiver_permission(
            db, uuid.uuid4(), uuid.uuid4(), "can_do_something_invalid"
        )
        assert result is False
        # Should not even query the database
        db.execute.assert_not_called()


# ── TestPermissionSchemaValidation ──


class TestPermissionSchemaValidation:
    """Tests for permission schemas."""

    def test_update_request_allows_partial(self):
        """PermissionsUpdateRequest allows omitting fields."""
        from src.schemas.caregiver_permissions import PermissionsUpdateRequest

        data = PermissionsUpdateRequest(can_view_glucose=False)
        dumped = data.model_dump(exclude_unset=True)
        assert dumped == {"can_view_glucose": False}

    def test_permissions_response_has_all_fields(self):
        """CaregiverPermissions includes all 5 fields with defaults."""
        from src.schemas.caregiver_permissions import CaregiverPermissions

        perms = CaregiverPermissions()
        assert perms.can_view_glucose is True
        assert perms.can_view_history is True
        assert perms.can_view_iob is True
        assert perms.can_view_ai_suggestions is False
        assert perms.can_receive_alerts is True

    def test_update_request_rejects_extra_fields(self):
        """PermissionsUpdateRequest rejects unknown fields."""
        from pydantic import ValidationError

        from src.schemas.caregiver_permissions import PermissionsUpdateRequest

        with pytest.raises(ValidationError, match="Extra inputs are not permitted"):
            PermissionsUpdateRequest(can_view_glucose=False, is_admin=True)


# ── TestPermissionEndpointRBAC ──


class TestPermissionEndpointRBAC:
    """Integration tests verifying RBAC on permission endpoints.

    These tests ensure unauthenticated users get 401 on all three
    permission endpoints (GET /linked, GET /linked/{id}/permissions,
    PATCH /linked/{id}/permissions).
    """

    @pytest.mark.asyncio
    async def test_unauthenticated_cannot_list_linked(self, client):
        """Unauthenticated requests to GET /linked return 401."""
        response = await client.get("/api/caregivers/linked")
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_unauthenticated_cannot_get_permissions(self, client):
        """Unauthenticated requests to GET /linked/{id}/permissions return 401."""
        fake_id = str(uuid.uuid4())
        response = await client.get(f"/api/caregivers/linked/{fake_id}/permissions")
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_unauthenticated_cannot_update_permissions(self, client):
        """Unauthenticated requests to PATCH /linked/{id}/permissions return 401."""
        fake_id = str(uuid.uuid4())
        response = await client.patch(
            f"/api/caregivers/linked/{fake_id}/permissions",
            json={"can_view_glucose": False},
        )
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_unauthenticated_patch_invalid_uuid_still_401(self, client):
        """Auth check fires before path validation — invalid UUID still 401."""
        response = await client.patch(
            "/api/caregivers/linked/not-a-uuid/permissions",
            json={"can_view_glucose": False},
        )
        # Auth middleware rejects before FastAPI validates path params
        assert response.status_code == 401
