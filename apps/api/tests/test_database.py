"""Tests for database module.

Story 1.2: Database Migrations & Health Endpoint
Tests for database connectivity and session management.
"""

from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from src.database import check_database_connection


class TestDatabaseConnection:
    """Tests for database connection utilities."""

    @pytest.mark.asyncio
    async def test_check_database_connection_returns_true_when_connected(self):
        """
        check_database_connection returns True when database is reachable.
        """
        with patch("src.database.get_engine") as mock_get_engine:
            # Create mock connection context
            mock_conn = AsyncMock()
            mock_conn.execute = AsyncMock()

            mock_connect = AsyncMock()
            mock_connect.__aenter__ = AsyncMock(return_value=mock_conn)
            mock_connect.__aexit__ = AsyncMock(return_value=None)

            mock_engine = MagicMock()
            mock_engine.connect.return_value = mock_connect
            mock_get_engine.return_value = mock_engine

            result = await check_database_connection()

            assert result is True

    @pytest.mark.asyncio
    async def test_check_database_connection_returns_false_on_exception(self):
        """
        check_database_connection returns False when database connection fails.
        """
        with patch("src.database.get_engine") as mock_get_engine:
            mock_engine = MagicMock()
            mock_engine.connect.side_effect = Exception("Connection refused")
            mock_get_engine.return_value = mock_engine

            result = await check_database_connection()

            assert result is False
