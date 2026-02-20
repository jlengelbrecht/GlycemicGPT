# Branching Strategy

## Branch Model

| Branch | Purpose | Protected | Default |
|--------|---------|-----------|---------|
| `main` | Stable releases | Yes | Yes |
| `develop` | Integration / dev testing | Yes | No |

## Full Release Cycle

```
1. Create feature branch from develop
2. Open PR targeting develop, CI runs
3. Squash-merge to develop
4. (Automated) Dev Docker images tagged "dev", debug APK published as dev-latest pre-release
5. Repeat 1-4 for more features, test dev builds
6. When ready for stable release: create promotion PR (develop -> main)
7. CI runs on the promotion PR
8. Merge with "Rebase and merge" (individual commits land on main)
9. (Automated) release-please creates version bump PR on main
10. (Automated) homebot auto-merges the version bump PR
11. (Automated) GitHub Release created, signed APK uploaded, Docker images tagged with version + "latest"
12. Sync develop: rebase onto main to pick up version bump
```

## Feature Branch Workflow

1. Create a feature branch from `develop`:
   ```bash
   git checkout develop && git pull
   git checkout -b feat/my-feature
   ```
2. Push and create a PR targeting `develop`.
3. CI runs on the PR. **Squash-merge** when approved.

## Promotion (develop -> main)

A "promotion PR" is a regular pull request that moves tested code from develop to main:

```bash
gh pr create --base main --head develop \
  --title "chore: promote develop to main" \
  --body-file .github/PROMOTION_PR_TEMPLATE.md
```

**IMPORTANT: Use "Rebase and merge", not "Squash and merge".** Rebase merge preserves each feature as an individual commit on main. This is critical for the CHANGELOG -- release-please needs to see each `feat:` and `fix:` commit separately to generate granular changelog entries. Squash merge would collapse everything into one unhelpful entry.

After the promotion PR merges:
- **release-please** sees the individual commits and creates a version bump PR
- homebot auto-merges the version bump PR
- A GitHub Release is created with signed APKs and versioned Docker images

### Post-release develop sync

The version bump commit exists on main but not develop. Sync them:

```bash
git fetch origin
git checkout develop
git rebase origin/main
```

To push: temporarily disable the `non_fast_forward` rule on the develop ruleset, then:
```bash
git push origin develop --force-with-lease
```
Re-enable the rule immediately after.

## Release Channels

### Stable (main)

- **Docker images:** Tagged `latest` and semver (`1.2.3`, `1.2`)
- **Mobile APKs:** Signed release APKs uploaded to GitHub Releases
- **Update check:** Release builds fetch `/releases/latest`

### Dev (develop)

- **Docker images:** Tagged `dev` (overwritten on each push to develop)
- **Mobile APKs:** Debug APKs uploaded to a rolling `dev-latest` pre-release
- **Update check:** Debug builds fetch `/releases/tags/dev-latest`

The `/releases/latest` API endpoint automatically excludes pre-releases, so `dev-latest` never interferes with stable update checks.

## What Stays the Same

- **release-please** config and workflow: no changes, still watches main
- **CHANGELOG.md** format: unchanged, generated from individual commits on main
- **release.yml**: still triggers on main push, builds signed APKs
- **CI**: all workflows run on both branches, PRs can target either

## Renovate

Dependency update PRs target `develop` via the `baseBranches` config. They flow to main through the promotion process.
