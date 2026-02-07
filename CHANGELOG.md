# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Features

- Epic 1: Foundation & Safety Compliance (Stories 1.1-1.5)
- Epic 2: User Authentication & Account Management (Stories 2.1-2.4)
- Epic 3: Data Source Connection (Stories 3.1-3.7)
  - Dexcom CGM integration
  - Tandem pump integration with Control-IQ parsing
  - IoB projection engine with 4-hour decay curve
  - Data freshness display component

### Infrastructure

- Initial project scaffolding with FastAPI and Next.js
- Docker and Kubernetes deployment manifests
- PostgreSQL database with Alembic migrations
- CI/CD workflows with GitHub Actions
- Semantic versioning with release-please
