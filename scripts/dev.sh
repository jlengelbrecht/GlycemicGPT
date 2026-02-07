#!/bin/bash
# Development environment startup script

set -e

echo "Starting GlycemicGPT development environment..."

# Check if .env exists
if [ ! -f .env ]; then
    echo "Creating .env from .env.example..."
    cp .env.example .env
fi

# Start services
docker compose up --build "$@"
