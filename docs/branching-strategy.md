# Branching Strategy

## Branch Model

| Branch | Purpose | Protected | Default |
|--------|---------|-----------|---------|
| `main` | Stable releases | Yes | No |
| `develop` | Integration / next release | Yes | Yes |

> **Setup required:** The GitHub default branch must be changed from `main` to `develop` and branch protection rules applied to `develop` before this model is active.

## Feature Branch Workflow

1. Create a feature branch from `develop`:
   ```bash
   git checkout develop && git pull
   git checkout -b feat/my-feature
   ```
2. Push and create a PR targeting `develop`.
3. CI runs on the PR. Squash-merge when approved.

## Promotion (develop -> main)

When develop is ready for a stable release:

```bash
gh pr create --base main --head develop \
  --title "chore: promote develop to main" \
  --body-file .github/PROMOTION_PR_TEMPLATE.md
```

After the promotion PR merges:
- **release-please** creates a version bump PR on main
- Merging that PR triggers the stable release (changelog, GitHub release, signed APKs, Docker images tagged with semver + `latest`)

### Post-promotion rebase

Squash-merge creates divergence between develop and main. Rebase develop onto main after promotion:

```bash
git fetch origin
git checkout develop
git rebase origin/main
# Force push required -- temporarily disable branch protection on develop first
git push origin develop --force-with-lease
```

> **Note:** Branch protection on `develop` blocks force pushes. Temporarily disable protection before this step, then re-enable it immediately after.

## Release Channels

### Stable (main)

- **Docker images:** Tagged `latest` and semver (`1.2.3`, `1.2`)
- **Mobile APKs:** Signed release APKs uploaded to GitHub Releases
- **Update check:** App fetches `/releases/latest` (release builds)

### Dev (develop)

- **Docker images:** Tagged `dev` (overwritten on each push to develop)
- **Mobile APKs:** Debug APKs uploaded to a rolling `dev-latest` pre-release
- **Update check:** App fetches `/releases/tags/dev-latest` (debug builds)

The `/releases/latest` API endpoint automatically excludes pre-releases, so `dev-latest` never interferes with stable update checks.

## CI Triggers

All CI workflows (lint, test, build, Docker integration) run on both `main` and `develop` branches. PRs can target either branch.

## Renovate

Dependency update PRs target `develop` via the `baseBranches` config. They flow to main through the promotion process.
