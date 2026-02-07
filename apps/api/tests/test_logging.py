"""Story 1.5: Tests for structured logging configuration."""

import json
import logging
from io import StringIO

import pytest

from src.logging_config import (
    JsonFormatter,
    StructuredLogger,
    TextFormatter,
    correlation_id_ctx,
    get_logger,
    setup_logging,
)


class TestJsonFormatter:
    """Tests for JSON log formatting."""

    def test_json_format_basic(self):
        """Test basic JSON log format."""
        formatter = JsonFormatter(service_name="test-service")
        record = logging.LogRecord(
            name="test.logger",
            level=logging.INFO,
            pathname="test.py",
            lineno=10,
            msg="Test message",
            args=(),
            exc_info=None,
        )

        output = formatter.format(record)
        parsed = json.loads(output)

        assert parsed["level"] == "INFO"
        assert parsed["service"] == "test-service"
        assert parsed["message"] == "Test message"
        assert parsed["logger"] == "test.logger"
        assert "timestamp" in parsed

    def test_json_format_with_correlation_id(self):
        """Test JSON format includes correlation ID when set."""
        formatter = JsonFormatter()
        record = logging.LogRecord(
            name="test",
            level=logging.INFO,
            pathname="",
            lineno=0,
            msg="Test",
            args=(),
            exc_info=None,
        )

        # Set correlation ID
        token = correlation_id_ctx.set("test-correlation-123")
        try:
            output = formatter.format(record)
            parsed = json.loads(output)
            assert parsed["correlation_id"] == "test-correlation-123"
        finally:
            correlation_id_ctx.reset(token)

    def test_json_format_without_correlation_id(self):
        """Test JSON format works without correlation ID."""
        formatter = JsonFormatter()
        record = logging.LogRecord(
            name="test",
            level=logging.INFO,
            pathname="",
            lineno=0,
            msg="Test",
            args=(),
            exc_info=None,
        )

        output = formatter.format(record)
        parsed = json.loads(output)
        assert "correlation_id" not in parsed

    def test_json_format_error_includes_location(self):
        """Test that ERROR level logs include location info."""
        formatter = JsonFormatter()
        record = logging.LogRecord(
            name="test",
            level=logging.ERROR,
            pathname="/app/test.py",
            lineno=42,
            msg="Error occurred",
            args=(),
            exc_info=None,
        )
        record.funcName = "test_function"

        output = formatter.format(record)
        parsed = json.loads(output)

        assert "location" in parsed
        assert parsed["location"]["file"] == "/app/test.py"
        assert parsed["location"]["line"] == 42
        assert parsed["location"]["function"] == "test_function"

    def test_json_format_with_exception(self):
        """Test JSON format includes exception info."""
        formatter = JsonFormatter()

        try:
            raise ValueError("Test error")
        except ValueError:
            import sys

            exc_info = sys.exc_info()

        record = logging.LogRecord(
            name="test",
            level=logging.ERROR,
            pathname="",
            lineno=0,
            msg="Error",
            args=(),
            exc_info=exc_info,
        )

        output = formatter.format(record)
        parsed = json.loads(output)

        assert "exception" in parsed
        assert "ValueError" in parsed["exception"]


class TestTextFormatter:
    """Tests for text log formatting."""

    def test_text_format_basic(self):
        """Test basic text log format."""
        formatter = TextFormatter(service_name="test-service")
        record = logging.LogRecord(
            name="test",
            level=logging.INFO,
            pathname="",
            lineno=0,
            msg="Test message",
            args=(),
            exc_info=None,
        )

        output = formatter.format(record)

        assert "test-service" in output
        assert "INFO" in output
        assert "Test message" in output

    def test_text_format_with_correlation_id(self):
        """Test text format includes correlation ID."""
        formatter = TextFormatter()
        record = logging.LogRecord(
            name="test",
            level=logging.INFO,
            pathname="",
            lineno=0,
            msg="Test",
            args=(),
            exc_info=None,
        )

        token = correlation_id_ctx.set("abc-123")
        try:
            output = formatter.format(record)
            assert "[abc-123]" in output
        finally:
            correlation_id_ctx.reset(token)


class TestStructuredLogger:
    """Tests for StructuredLogger wrapper."""

    def test_logger_info(self, caplog):
        """Test structured logger info method."""
        logger = get_logger("test.logger")

        with caplog.at_level(logging.INFO):
            logger.info("Test info message")

        assert "Test info message" in caplog.text

    def test_logger_error(self, caplog):
        """Test structured logger error method."""
        logger = get_logger("test.logger")

        with caplog.at_level(logging.ERROR):
            logger.error("Test error message")

        assert "Test error message" in caplog.text

    def test_logger_with_extra_fields(self):
        """Test logger with extra fields (for JSON formatter)."""
        # This test verifies the method accepts extra fields
        logger = get_logger("test")
        # Should not raise any errors
        logger.info("Test message", user_id="123", action="login")


class TestSetupLogging:
    """Tests for logging setup function."""

    def test_setup_json_logging(self):
        """Test setup_logging with JSON format."""
        setup_logging(log_format="json", log_level="DEBUG")

        root = logging.getLogger()
        assert root.level == logging.DEBUG
        assert len(root.handlers) == 1
        assert isinstance(root.handlers[0].formatter, JsonFormatter)

    def test_setup_text_logging(self):
        """Test setup_logging with text format."""
        setup_logging(log_format="text", log_level="INFO")

        root = logging.getLogger()
        assert len(root.handlers) == 1
        assert isinstance(root.handlers[0].formatter, TextFormatter)

    def test_setup_custom_service_name(self):
        """Test setup_logging with custom service name."""
        setup_logging(log_format="json", service_name="custom-service")

        root = logging.getLogger()
        formatter = root.handlers[0].formatter
        assert isinstance(formatter, JsonFormatter)
        assert formatter.service_name == "custom-service"
