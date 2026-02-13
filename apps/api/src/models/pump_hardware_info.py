"""Story 16.6: Pump hardware info model.

Stores pump hardware identification and feature flags needed for
constructing the Tandem cloud upload payload.
"""

import uuid

from sqlalchemy import BigInteger, ForeignKey, String
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from src.models.base import Base, TimestampMixin


class PumpHardwareInfo(Base, TimestampMixin):
    """Hardware identification and feature flags for a Tandem pump.

    One record per user. Updated on each BLE connection when the mobile
    app reads the pump's hardware descriptors.
    """

    __tablename__ = "pump_hardware_info"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        primary_key=True,
        default=uuid.uuid4,
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
        unique=True,
        index=True,
    )

    serial_number: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
    )

    model_number: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
    )

    part_number: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
    )

    pump_rev: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
    )

    arm_sw_ver: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
    )

    msp_sw_ver: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
    )

    config_a_bits: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
    )

    config_b_bits: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
    )

    pcba_sn: Mapped[int] = mapped_column(
        BigInteger,
        nullable=False,
    )

    pcba_rev: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
    )

    pump_features: Mapped[dict] = mapped_column(
        JSONB,
        nullable=False,
        server_default="{}",
    )

    # Relationship to user
    user = relationship("User", back_populates="pump_hardware_info")

    def __repr__(self) -> str:
        return (
            f"<PumpHardwareInfo(user_id={self.user_id}, "
            f"serial={self.serial_number}, model={self.model_number})>"
        )
