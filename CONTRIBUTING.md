# 🤝 Contributing to GlycemicGPT

Thanks for your interest in contributing to GlycemicGPT! Whether you're fixing a typo, squashing a bug, or building a whole new feature -- we appreciate you. 💙

This guide covers everything you need to know to get started.

---

## ⚠️ Safety First -- Please Read

**GlycemicGPT interacts with real diabetes management data. Incorrect data display or bad suggestions can directly impact health decisions.**

Before writing any code, please understand these non-negotiable rules:

- 🏷️ **All** AI-generated outputs must be clearly labeled as **suggestions, not medical advice**
- 💉 Insulin dosing recommendations must **always** include safety disclaimers
- 🧪 Test thoroughly -- a wrong number on a glucose chart is not just a UI bug, it's a safety issue
- 🚫 **No unsupervised device control** -- see below

### Device Control & AI-Assisted Corrections

Currently, GlycemicGPT is **read-only** with respect to medical devices -- it reads data from pumps and CGMs but does not send commands to them. Do not add code that directly controls or modifies device settings.

**Future direction:** As the project matures, we plan to introduce AI-assisted correction suggestions through a rigorous multi-stage safety pipeline. The architecture looks like this:

1. **AI detection** -- AI monitors processed glucose data and identifies actionable patterns (e.g., persistent highs, missed corrections)
2. **Correction plan** -- AI proposes a specific action (e.g., a correction bolus) and passes it to the safety system
3. **Safety validation** -- The safety module independently validates the plan against the user's insulin-on-board, configured ratios, correction factors, and physiological limits. It can approve or reject.
4. **User consent** -- If the safety system approves, the user receives an alert with full transparency: the suggestion, the ratios it was calculated against, and all the math. The user must explicitly accept **and** confirm via biometric authentication (fingerprint or face ID).
5. **Final safety check** -- After user consent, the request passes through a second safety validation to re-verify all calculations before any action is taken
6. **Delivery or rejection** -- If the final check passes, the command goes to the delivery module. If it fails at any stage, the user is informed with a clear explanation of why.

This feature will **not** be enabled by default. Users must explicitly opt in on the backend. Any code contributing to this pipeline will undergo extraordinary scrutiny during review.

PRs that violate these safety principles will not be merged regardless of code quality.

---

## 📑 Table of Contents

- [Ways to Contribute](#ways-to-contribute)
- [Finding Something to Work On](#finding-something-to-work-on)
- [Development Setup](#development-setup)
- [Branching & Workflow](#branching--workflow)
- [Commit Messages](#commit-messages)
- [Before You Submit](#before-you-submit)
- [Pull Request Process](#pull-request-process)
- [Code Style](#code-style)
- [AI-Assisted Development & Attribution Policy](#ai-assisted-development--attribution-policy)
- [Project Structure](#project-structure)
- [Release Channels](#release-channels)
- [License](#license)
- [Questions?](#questions)

---

## 💡 Ways to Contribute

There are many ways to help, not all of them involve writing code:

- 🐛 **Report bugs** -- Use the [Bug Report](https://github.com/jlengelbrecht/GlycemicGPT/issues/new?template=bug_report.yml) or [Mobile App Issue](https://github.com/jlengelbrecht/GlycemicGPT/issues/new?template=mobile_report.yml) template
- ✨ **Request features** -- Use the [Feature Request](https://github.com/jlengelbrecht/GlycemicGPT/issues/new?template=feature_request.yml) template
- 📝 **Improve documentation** -- Typos, unclear instructions, missing guides
- 🧪 **Write tests** -- More coverage is always welcome
- 🔍 **Review PRs** -- Fresh eyes catch things automated checks can't
- 💬 **Answer questions** -- Help others in [Discussions](https://github.com/jlengelbrecht/GlycemicGPT/discussions)

Before opening an issue, please search [existing issues](https://github.com/jlengelbrecht/GlycemicGPT/issues?q=is%3Aissue) to avoid duplicates. For general questions and support, use [Discussions](https://github.com/jlengelbrecht/GlycemicGPT/discussions/categories/q-a) instead of creating an issue.

---

## 🔍 Finding Something to Work On

Not sure where to start? Browse [open issues](https://github.com/jlengelbrecht/GlycemicGPT/issues) and look for these labels:

- 🏷️ **`good first issue`** -- Small, well-scoped tasks ideal for new contributors
- 🏷️ **`help wanted`** -- We'd love community help on these
- 🏷️ **`bug`** -- Known bugs waiting for a fix

> **Tip:** Not every label will have open issues at all times. If none are tagged yet, browse the full [issue list](https://github.com/jlengelbrecht/GlycemicGPT/issues) or check the [Ideas discussion board](https://github.com/jlengelbrecht/GlycemicGPT/discussions/categories/ideas) for inspiration.

If you'd like to work on something, comment on the issue to let others know. For larger changes, please open an issue or start a [discussion](https://github.com/jlengelbrecht/GlycemicGPT/discussions) first to discuss the approach before investing time in a PR.

---

## 🛠️ Development Setup

### Prerequisites

You only need the tools for the component(s) you're working on:

| Component | You Need |
|-----------|----------|
| 🌐 Web UI | Docker + Docker Compose |
| 🐍 Backend API | Docker + Docker Compose (or Python 3.12+ with [UV](https://docs.astral.sh/uv/)) |
| 🤖 AI Sidecar | Docker + Docker Compose (or Node.js 20+) |
| 📱 Mobile App | JDK 17, Android SDK (API 34) |
| ⌚ Wear OS | JDK 17, Android SDK (API 34), Wear OS system image |
| 📝 Docs only | Just a text editor! |

### 🚀 Quick Start (Web/API -- recommended for most contributors)

The fastest way to get a working dev environment:

```bash
# 1. Fork and clone
git clone https://github.com/<your-username>/GlycemicGPT.git
cd GlycemicGPT

# 2. Add upstream remote
git remote add upstream https://github.com/jlengelbrecht/GlycemicGPT.git

# 3. Install the git commit-msg hook (strips prohibited attribution lines)
#    If scripts/hooks/commit-msg doesn't exist, skip this step -- CI enforces it too
cp scripts/hooks/commit-msg .git/hooks/commit-msg
chmod +x .git/hooks/commit-msg

# 4. Copy environment file (defaults work for local dev)
cp .env.example .env

# 5. Start all services
docker compose up --build -d

# 6. Verify everything is running
curl localhost:8000/health   # API -- should return {"status": "healthy"}
curl localhost:3456/health   # AI sidecar -- should return {"status": "ok"}
# Web UI is at http://localhost:3000
```

### Services

| Service | Port | Description |
|---------|------|-------------|
| Web UI | 3000 | Next.js 15 frontend |
| API | 8000 | FastAPI backend |
| AI Sidecar | 3456 | AI provider proxy |
| PostgreSQL | 5432 | Database |
| Redis | 6379 | Cache / SSE broker |

### 📱 Mobile App Setup (Android)

Only needed if you're working on the phone or Wear OS apps:

```bash
cd apps/mobile

# Build debug APKs (phone + Wear OS)
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest

# Run lint
./gradlew lintDebug
```

### 🐍 Backend Setup (without Docker)

If you prefer running the API directly:

> **Note:** Backend tests require a running PostgreSQL instance. The easiest way is to start just the database container: `docker compose up db -d`. Tests use the connection settings from your `.env` file.

```bash
cd apps/api
uv sync              # Install dependencies
uv run pytest        # Run tests (requires PostgreSQL -- see note above)
uv run ruff check .  # Lint
uv run ruff format --check .  # Format check
```

### 🖥️ Frontend Setup (without Docker)

If you prefer running the frontend directly:

```bash
cd apps/web
npm install    # Install dependencies
npm test       # Run tests
npm run lint   # Lint
npm run dev    # Start dev server (http://localhost:3000)
```

> **Note:** Running `npm run dev` alone only tests frontend rendering. For full integration testing (API calls, auth, SSE), use Docker Compose.

### 🤖 Sidecar Setup (without Docker)

```bash
cd sidecar
npm install    # Install dependencies
npm test       # Run tests
```

---

## 🌿 Branching & Workflow

We use a **develop/main** branching model:

```
feature branch --> squash merge --> develop --> rebase merge --> main
                                      |                           |
                                  dev builds                  stable releases
                                  debug APKs                  signed APKs
                                  Docker :dev                 Docker :latest
```

### Rules

- **`develop`** is the integration branch. **All contributor PRs target `develop`.**
- **`main`** is the stable release branch. Do **not** target PRs to `main`.
- Feature branches are created from `develop` and squash-merged back.

### Creating a Feature Branch

```bash
git checkout develop && git pull
git checkout -b feat/my-feature
# ... make changes ...
git push -u origin feat/my-feature
# Create PR targeting develop
```

### Branch Naming

Use a descriptive prefix:

| Prefix | Usage |
|--------|-------|
| `feat/` | New features |
| `fix/` | Bug fixes |
| `docs/` | Documentation |
| `refactor/` | Code restructuring |
| `ci/` | CI/CD changes |

---

## 📝 Commit Messages

We use [Conventional Commits](https://www.conventionalcommits.org/). This drives our automated CHANGELOG generation via [release-please](https://github.com/googleapis/release-please).

| Prefix | Usage | CHANGELOG |
|--------|-------|-----------|
| `feat:` | New features | Visible |
| `fix:` | Bug fixes | Visible |
| `perf:` | Performance improvements | Visible |
| `docs:` | Documentation only | Visible |
| `refactor:` | Code restructuring | Visible |
| `ci:` | CI/CD changes | Visible |
| `chore:` | Maintenance, deps | Hidden |
| `test:` | Adding/updating tests | Hidden |

Prefixes marked "Hidden" won't appear in the CHANGELOG but are still good practice.

**Examples:**
```
feat: add glucose trend chart to mobile dashboard
fix: prevent token refresh race condition on concurrent 401s
docs: add contributing guide and issue templates
refactor: extract BLE packet parser into separate module
chore(deps): update dependency next to v15.5.12
```

---

## ✅ Before You Submit

**Run these checks locally before pushing.** CI will catch failures, but it's faster to catch them yourself.

### Pre-Push Checklist

Run the checks for whichever component(s) you changed:

**Backend (API):** (requires PostgreSQL -- run `docker compose up db -d` if not already running)
```bash
cd apps/api
uv run pytest                  # Unit tests
uv run ruff check .            # Linter
uv run ruff format --check .   # Formatter
```

**Frontend (Web):**
```bash
cd apps/web
npm test       # Unit tests
npm run lint   # Linter
npm run build  # Build check (catches TypeScript errors)
```

**Mobile (Android):**
```bash
cd apps/mobile
./gradlew testDebugUnitTest  # Unit tests (phone + Wear OS)
./gradlew lintDebug          # Lint (phone + Wear OS)
./gradlew assembleDebug      # Build check
```

**AI Sidecar:**
```bash
cd sidecar
npm test  # Unit tests
```

**Docker Integration (if you changed Docker/compose files or cross-service behavior):**
```bash
docker compose up --build -d
curl localhost:8000/health       # Should return {"status": "healthy"}
curl localhost:3456/health       # Should return {"status": "ok"}
docker compose down
```

### Final Checks

- [ ] All tests pass for the component(s) you changed
- [ ] Linting passes with no new warnings
- [ ] No hardcoded secrets, API keys, tokens, or credentials in your code
- [ ] New functionality has tests
- [ ] Commit messages follow [Conventional Commits](#commit-messages) format
- [ ] Your branch is up to date with `develop`

---

## 🔀 Pull Request Process

### Creating Your PR

1. Push your feature branch to your fork
2. Open a PR **targeting `develop`** (not `main`)
3. Fill out the PR template completely
4. Link related issues using `Fixes #123` or `Relates to #123`

### What Happens Next

1. **CI runs automatically** -- all required checks must pass (see below)
2. **Code owner review** -- a maintainer will review your PR
3. **Feedback** -- you may be asked to make changes; push new commits to the same branch
4. **Merge** -- once approved and CI passes, a maintainer will squash-merge your PR

### Required CI Checks

Every PR must pass these checks before it can be merged:

| Check | What It Validates |
|-------|-------------------|
| Backend Tests | Python unit tests with PostgreSQL |
| Backend Lint | Ruff linter + formatter |
| Frontend Tests | Jest tests + Next.js build (`npm test` and `npm run build`) |
| Frontend Lint | ESLint |
| Sidecar Tests | Vitest for AI proxy |
| Attribution Check | No prohibited attribution lines |
| GitGuardian Security Checks | Secret/credential scanning |

Additionally, PRs that modify `apps/mobile/**` will run Android Build & Test (unit tests, lint, debug APK build).

> **Note:** There is a separate [Promotion PR Template](.github/PROMOTION_PR_TEMPLATE.md) used only for develop-to-main releases. Regular contributors don't need to worry about this.

---

## 🎨 Code Style

### 🐍 Python (Backend -- `apps/api/`)

- Formatter: **Ruff** (`ruff format`)
- Linter: **Ruff** (`ruff check`)
- Type hints required for function signatures
- FastAPI patterns: use `Depends()` for dependency injection

### 🟦 TypeScript (Frontend -- `apps/web/`)

- Formatter/Linter: **ESLint** + **Prettier**
- Next.js 15 App Router conventions
- React Server Components by default; `"use client"` only when needed
- Tailwind CSS for styling; shadcn/ui for components

### 🟣 Kotlin (Mobile -- `apps/mobile/`)

- Standard Kotlin conventions
- Jetpack Compose for UI
- Hilt for dependency injection
- Room for local database
- Coroutines + Flow for async operations

### 🤖 TypeScript (Sidecar -- `sidecar/`)

- Express.js REST patterns
- Vitest for testing
- Multi-provider AI proxy (routes requests to Claude, OpenAI, Ollama, etc.)
- Follows same TypeScript conventions as frontend

---

## 🤖 AI-Assisted Development & Attribution Policy

Let's be real -- the code owners didn't write every line of this project by hand, and we don't expect you to either. **Using AI tools (Claude, Copilot, ChatGPT, Cursor, etc.) to help write code is completely fine.** We'd be hypocrites if we said otherwise.

That said, there's a difference between using AI as a tool and blindly pasting whatever it spits out. We don't want vibe-coded junk. **You are responsible for the code you submit**, regardless of who (or what) helped write it.

### What We Expect

- **Understand your code.** If you can't explain what a function does and why, don't submit it.
- **Match existing patterns.** AI tools love to invent their own conventions. Make sure AI-generated code follows _our_ code style, architecture, and naming patterns -- not whatever the model hallucinated.
- **Test it.** AI-generated code is especially prone to subtle bugs. Run the tests. Add new ones if needed.
- **Review it yourself.** Do a self-review of every AI-generated line before pushing. Treat AI output like a junior developer's first draft -- helpful starting point, needs a careful eye.

### No AI Attribution in Code

This one is non-negotiable. Our repo must be **clean of AI attribution lines**. That means:

- **No** `Co-Authored-By: Claude`, `Generated by ChatGPT`, or similar lines in commits
- **No** `// Generated by AI` or `// Copilot suggestion` comments in code
- **No** AI tool branding, promotional links, or attribution banners in PR descriptions

We have a CI check (**Attribution Check**) that automatically catches common AI attribution patterns and will fail your PR if any are found. The git commit-msg hook (installed during [Quick Start](#quick-start-webapi----recommended-for-most-contributors)) also strips these locally before they reach the repo.

Why? Because attribution to a tool that can't be held accountable for code quality is meaningless noise. The _contributor_ is the author. Own it.

---

## 📁 Project Structure

```
GlycemicGPT/
├── apps/
│   ├── api/            # FastAPI backend (Python)
│   │   ├── src/        # Source code
│   │   └── tests/      # pytest tests
│   ├── web/            # Next.js 15 frontend (TypeScript)
│   │   ├── src/        # Source code (App Router)
│   │   └── __tests__/  # Jest tests
│   └── mobile/         # Android app (Kotlin)
│       ├── app/        # Phone app module
│       └── wear/       # Wear OS module
├── sidecar/            # AI provider proxy (TypeScript/Express)
│   ├── src/            # Source code
│   └── tests/          # Vitest tests
├── docker-compose.yml  # Full stack orchestration
├── .github/
│   ├── workflows/      # CI/CD pipelines
│   ├── CODEOWNERS      # Code ownership for PR reviews
│   └── ISSUE_TEMPLATE/ # Issue templates
└── docs/               # Project documentation
```

---

## 📦 Release Channels

| Channel | Branch | Docker Tag | APK Type |
|---------|--------|------------|----------|
| **Stable** | `main` | `latest`, semver | Signed release APK |
| **Dev** | `develop` | `dev` | Debug APK |

Stable releases are created automatically by release-please when code is promoted from `develop` to `main`. Your contribution will ship in the next stable release after the promotion PR is merged.

---

## 📜 License

GlycemicGPT is licensed under the [GNU General Public License v3.0](LICENSE). By contributing, you agree that your contributions will be licensed under the same license.

---

## 💬 Questions?

- 🙏 **General questions & help** -- Post in [Q&A Discussions](https://github.com/jlengelbrecht/GlycemicGPT/discussions/categories/q-a)
- 💡 **Feature ideas & brainstorming** -- Post in [Ideas Discussions](https://github.com/jlengelbrecht/GlycemicGPT/discussions/categories/ideas)
- 🐛 **Bug reports** -- Open an [Issue](https://github.com/jlengelbrecht/GlycemicGPT/issues/new/choose) using the appropriate template
- 🙌 **Show off your setup** -- Post in [Show and Tell](https://github.com/jlengelbrecht/GlycemicGPT/discussions/categories/show-and-tell)

Please **do not** open Issues for general questions -- use Discussions instead. Issues are for actionable bugs and feature requests.

We try to respond to PRs, issues, and discussions within a few days. If your PR sits without feedback for more than a week, feel free to leave a comment pinging the maintainers.
