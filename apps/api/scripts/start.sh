#!/bin/bash
set -e

echo "Running database migrations..."
python -c "
from src.core.migrations import run_migrations
run_migrations()
"
echo "Migrations complete."

echo "Starting API server..."
# Use --no-access-log to disable uvicorn access logs (we log requests via middleware)
# Use --log-level warning to reduce uvicorn noise (our app handles logging)
exec uvicorn src.main:app --host 0.0.0.0 --port 8000 --no-access-log --log-level warning
