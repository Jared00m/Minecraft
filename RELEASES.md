# Release Workflow

Purpose
- Document a safe process to produce testable builds (RCs) and publish final releases.

Branching and working copies
- Use versioned branches for work: `dev/1.26.2`, `hotfix/1.26.2`, `release/1.26.2`.
- Keep `versions/` folders only for released snapshots; branches are the source of truth.

Typical flow
1. Create a versioned development branch and work there:
   - `git checkout -b dev/1.26.2`
   - `git push -u origin dev/1.26.2`
2. Open a Pull Request to `development` or `main`. CI will build the artifact.
3. Download the CI artifact and deploy it to a staging server for testing.
4. If testing passes, tag an RC (release candidate) and push the tag:
   - `git tag -a v1.26.2-rc1 -m "RC 1.26.2"`
   - `git push origin v1.26.2-rc1`
   You can create a Draft or Pre-release on GitHub for `-rc` tags so it is available but not final.
5. After further testing, create a final release tag and push:
   - `git tag -a v1.26.2 -m "Release 1.26.2"`
   - `git push origin v1.26.2`

CI and releases
- Current CI builds on push/PR and uploads the JAR as an artifact. Use that artifact to test before releasing.
- Optional automation: CI can create Draft releases for `-rc` tags and publish releases when `vX.Y.Z` tags are pushed.

Releasing with `gh` (optional)
- Create a draft RC release:
  `gh release create v1.26.2-rc1 --draft --title "v1.26.2-rc1" --notes "RC for testing"`
- Publish final release:
  `gh release create v1.26.2 --title "v1.26.2" --notes "Release notes"` (attach JAR or let CI attach it)

Notes
- Prefer branches for working versions rather than untagged local folders — branches make CI, PRs, and merges clearer.
- Keep RCs as draft/pre-release until verified; only publish final `vX.Y.Z` when ready.

Test change
