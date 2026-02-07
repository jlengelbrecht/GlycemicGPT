FROM python:3.12-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install uv for fast package management
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

# Copy dependency files and README for package metadata
COPY pyproject.toml README.md ./

# Copy source code (needed for package install)
COPY src/ ./src/

# Install dependencies using uv
RUN uv pip install --system --no-cache .

# Copy remaining files
COPY migrations/ ./migrations/
COPY alembic.ini ./
COPY scripts/ ./scripts/

# Make start script executable
RUN chmod +x ./scripts/start.sh

# Expose port
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8000/health || exit 1

# Run migrations and start the application
CMD ["./scripts/start.sh"]
