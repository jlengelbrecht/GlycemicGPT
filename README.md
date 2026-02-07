# GlycemicGPT

AI-powered diabetes management platform - your on-call endo at home.

## Overview

GlycemicGPT bridges the gap between diabetes device data (Dexcom G7 CGM, Tandem t:slim pump) and actionable AI-powered insights.

**Key Principles:**
- **Suggestions only** - never controls medical devices
- **BYOAI architecture** - users bring their own AI (Claude/OpenAI)
- **Self-hosted** - Docker/Kubernetes deployment
- **Safety-first** - pre-validation layer, emergency escalation

## Quick Start

```bash
# Clone the repository
git clone https://github.com/jlengelbrecht/GlycemicGPT.git
cd GlycemicGPT

# Copy environment file
cp .env.example .env

# Start all services
docker compose up
```

Services will be available at:
- **Web UI:** http://localhost:3000
- **API:** http://localhost:8000
- **API Docs:** http://localhost:8000/docs

## Architecture

- **Frontend:** Next.js 15 with React 19, Tailwind CSS, shadcn/ui
- **Backend:** FastAPI with Python 3.12
- **Database:** PostgreSQL 16 with SQLAlchemy 2.0
- **Cache:** Redis 7

## Development

```bash
# Start development environment
./scripts/dev.sh

# Or with docker compose directly
docker compose up --build
```

## License

MIT License - See LICENSE file for details.

## Disclaimer

This is experimental open-source software. AI can and will make mistakes. Not FDA approved for medical use. Always consult your healthcare provider.
