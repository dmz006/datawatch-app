# BL419: gradle/actions update (held on v5)

**Status:** backlog, unscheduled. Opened 2026-10-09.
**Operator decision (2026-10-09):** stay on gradle/actions **v5** for now, and plan the update the same way as xterm.js (BL412 and `xterm-update-check.yml`): watch upstream, then upgrade as a separate, tested change.

## Why it's held

gradle/actions v6 moved its build caching into `gradle-actions-caching`. That component is proprietary and comes under Gradle's separate terms of use instead of the MIT licence. Taking v6 means accepting those terms for this repository's CI.

v5.0.2 is MIT-licensed, runs on Node 24, and is green on every workflow (2026-10-09). `.github/dependabot.yml` ignores gradle/actions major versions, so Dependabot won't reopen the v6 PR (#238 applied everything else).

## Where it's used (main, 2026-10-09)

| Workflow | Action |
|---|---|
| ci.yml | `wrapper-validation@v5`, `setup-gradle@v5` |
| release.yml (4 jobs) | `setup-gradle@v5` |
| security.yml, ios-build.yml | `setup-gradle@v5` |
| codeql.yml (2), android-screenshots.yml, ios-screenshots.yml | `setup-gradle` pinned to the v5.0.2 SHA |
| dependency-review.yml | `dependency-submission` pinned to the v5.0.2 SHA |

## Plan

1. **Watch (like xterm):** a monthly check reports when a new gradle/actions release appears. It covers v5.x patches, which Dependabot still proposes, and new v6+ majors. It also flags any change to the caching component's licence or terms. It keeps one tracking issue updated instead of opening PRs. Not built yet: build it when this item is scheduled, or sooner if the operator asks.
2. **Re-decide at each trigger.** One of:
   - **v5 reaches end of support or stops working** (for example, a Node runtime is retired on GitHub runners, or GitHub deprecates something it depends on);
   - Gradle changes the caching terms or licence;
   - BL418 needs a v6-only feature for AGP 9 or Gradle 10.

   The options are the same as before:
   - accept the terms and move to v6;
   - stay on v5;
   - drop gradle/actions caching and use `actions/cache` on `~/.gradle/caches` and `~/.gradle/wrapper`. Wrapper validation and dependency submission would still need gradle/actions, or a replacement.
3. **Upgrade as its own change:** a branch that updates every usage in the table above. Pin by SHA, with the version in a comment.
4. **Verify before merging:**
   - CI;
   - iOS Build;
   - CodeQL;
   - Security;
   - dependency-review, with the dependency graph submitted;
   - cache hit/miss on a second run.

   Also run the release workflow on a branch, without tagging, to confirm the four release jobs still set up Gradle.

## Risks

- **Release pipeline:** `release.yml` only runs on tags. Validate on a branch first (same rule as BL418).
- **Cache behaviour:** if caching changes, build times change too. Compare CI duration before and after.
- **Licence:** confirm the terms in force at upgrade time. Don't rely on this note.

Related: BL418 (Android/KMP toolchain migration), BL412 (xterm.js, the model for this plan).
