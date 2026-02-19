## Promotion: develop -> main

### Pre-merge checklist

- [ ] CI passes on develop
- [ ] Dev Docker images smoke-tested (`ghcr.io/.../glycemicgpt-*:dev`)
- [ ] Dev APK tested on device (from `dev-latest` release)
- [ ] No known regressions

### Notable changes since last promotion

<!-- List significant changes included in this promotion -->

-

### Post-merge steps

1. Wait for release-please to create a version bump PR on main
2. Merge the release-please PR (auto-merges if configured)
3. Verify stable container images are published with new version tag
4. Verify signed release APK is uploaded to the GitHub release
5. Rebase develop onto main (squash-merge creates divergence):
   ```bash
   git fetch origin && git checkout develop && git rebase origin/main
   # Force push required -- temporarily disable branch protection on develop first
   git push origin develop --force-with-lease
   ```
