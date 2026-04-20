# AGENT.md — datawatch-app (mobile client) Guardrails

**User-facing product name:** `datawatch` (lowercase, ADR-0041).
**Repo name:** `dmz006/datawatch-app`. **Technical namespaces:** `com.dmzs.datawatchclient[.dev]`.

This file defines operating rules for Claude when working on the **datawatch-mobile codebase**
(GH: `dmz006/datawatch-app`, brand home: `dmzs.com`).
Parent project: `github.com/dmz006/datawatch` — its AGENT.md is the source of truth for any
rule not restated or contradicted here.

---

## Pre-Execution Rule

Before executing any user prompt that involves code changes, new features, or bug fixes:

1. **Re-read AGENT.md rules** relevant to the task (planning, documentation, versioning, testing, etc.)
2. **Verify compliance** — ensure the planned approach follows all applicable rules.
3. **Flag conflicts** — if the prompt conflicts with a rule, notify the user before proceeding.

## Decision Making (inherited from parent, strengthened)

Every architectural, dependency, UX, or release trade-off requires explicit user approval.

1. **Do not guess.** Ask the user.
2. **Record the answer** as an ADR under `docs/decisions/NNNN-<slug>.md` using MADR format.
3. Never re-litigate an approved ADR. If new info invalidates one, write a *superseding* ADR
   that references the old one and ask the user to confirm.

## Project Identity (non-negotiable invariants)

These invariants are load-bearing and enforced by every rule below. If a task appears to
violate them, stop and confirm with the user:

- **Pure client.** The app runs no server process, no local LLM, no local session agent.
  Parity = client-side access to one or more datawatch servers.
- **No autonomous offline queue.** Writes fail fast with visible errors and user-driven retry
  (ADR-0013). Audio blobs may persist in the retry UI (ADR-0027); nothing else does.
- **Closed loop.** No third-party analytics, crashlytics-SaaS, or telemetry. FCM (push wake)
  and Google Drive Auto Backup (encrypted DB) are the only platform dependencies that send
  data off-device. Both are disclosable and user-controllable.
- **Single-user v1.** Interfaces abstract around `Principal` for future multi-user, but the
  UI ships single-role.

## Scope Constraints

- Work only within the `datawatch-mobile` repository directory.
- Do not read, write, or execute files outside this repository unless explicitly instructed.
- Do not modify system files, install packages, or change system configuration without user confirmation.
- The parent `datawatch` repo is read-only from this repo's perspective; if a parent change
  is needed (e.g., new server endpoint for mobile device registration), open an issue in
  `dmz006/datawatch` and wait for a release before depending on it.

## Replicate, don't reinvent (parent-first rule)

Before designing any behaviour that the parent PWA already implements — terminal handling,
WebSocket framing, session lifecycle, notification channels, voice capture, etc. — **check
`dmz006/datawatch` first** and replicate the parent's approach. The PWA is the source of
truth for user-facing behaviour; mobile is a client of the same hub protocol, not a
parallel implementation.

- Look at `internal/server/web/app.js`, `internal/server/web/index.html`, and
  `docs/api/openapi.yaml` in the parent repo (or fetch them live from a running server at
  `https://<host>/app.js`, `https://<host>/api/openapi.yaml`).
- Copy message-type names, frame shapes, throttle values, retry intervals, and state
  transitions verbatim. Deviations require an ADR recording *why* mobile needs to differ
  (e.g. platform-specific constraint).
- If the parent PWA has not solved the problem, open a `dmz006/datawatch` issue and
  coordinate — do not invent a mobile-only protocol extension that the hub won't honour.
- When in doubt, re-read the PWA source. It has already paid the debugging tax for edge
  cases (frame throttling, transitional-frame skip, minCols enforcement, reconnect
  watchdog); re-paying it here is waste.

## Code Quality Rules

- All Kotlin code must compile with `./gradlew build` and pass `detekt`, `ktlint`, and
  `android lint` with zero new warnings.
- All new modules must include a `package-info` KDoc (`package.kt`) describing purpose.
- Public API of the `:shared` KMP module is versioned — changes require a minor bump.
- The `TransportClient`, `PushBackend`, and `VoiceCapture` interfaces must remain stable —
  changes are breaking.
- Do not remove existing public composables or ViewModels without a major version bump.
- All new settings fields must have a corresponding entry in `docs/implementation.md`.
- Target close to 100% coverage on new/changed logic. Tests must be functional, not skeletons.

## Testing Tracker Rules

`docs/testing-tracker.md` maintains two levels of validation per feature/surface:

1. **Unit/integration tests** — JUnit5 (shared), Turbine (Flow), Robolectric/Compose UI test,
   MockWebServer for transport, SQLDelight test drivers. `./gradlew test` and
   `./gradlew connectedAndroidTest`.
2. **Live validation** — real device against a real `datawatch` server. Document what was
   tested, how, and what was observed (device model, Android version, server version, network
   transport used).

- **Tested=Yes** means Kotlin unit/integration tests exist and pass.
- **Validated=Yes** means a human or Maestro flow confirmed the feature end-to-end on a real
  device with a real datawatch server.
- **Never mark Validated=Yes from unit tests alone.** A live connection must be confirmed.
- Wear OS and Android Auto features require a separate live test row each — a passing phone
  test does not validate watch or car surfaces.

## Git Discipline

- Every logical change gets its own commit. Conventional commits:
  `type(scope): description` — `feat`, `fix`, `docs`, `chore`, `refactor`, `test`, `perf`, `ci`.
- Do not squash history. Each commit should be meaningful and reversible.
- Do not force-push to `main`.
- Branch protection on `main`: required checks = build, test, detekt, ktlint, lint.
- Solo dev + Claude workflow: direct commit to `main` is allowed post-scaffold; PRs are used
  when the user wants to stage a larger change before merge.

## Versioning

**1.0.0 is reserved (ADR-0043).** The v1.0.0 tag labels the release that
reaches 100 % client-side parity with the parent PWA at
[dmz006/datawatch](https://github.com/dmz006/datawatch/) — every row in
`docs/parity-status.md` flipped to ✅. Do NOT bump to 1.0.0 for any
other reason. Until parity is reached, all releases are 0.x. Minor
bumps inside 0.x still follow the rules below.

**Version bump rules:**
- **Every completed feature** (new screen, new transport, new Wear/Auto surface) = **minor**
  (e.g. `0.7.4` → `0.8.0`) unless user designates otherwise.
- **Bug fixes, docs, refactors, config changes** = **patch** (e.g. `0.7.4` → `0.7.5`).
- **Breaking changes** (user must explicitly request) = **major** — but in this project,
  major (1.0.0) is gated on PWA parity per ADR-0043, not on breakage alone.

**Version string lives in THREE places — update together in every release commit:**
- `composeApp/build.gradle.kts` — `versionName = "X.Y.Z"` and `versionCode` monotonic
- `wear/build.gradle.kts` — same `versionName`, matching `versionCode`
- `shared/src/commonMain/kotlin/com/dmzs/datawatchclient/Version.kt` — `const val VERSION = "X.Y.Z"`

**Pre-release version check**: a CI step greps all three and fails if mismatched.
**Never reuse a versionCode.** Play Store rejects re-uploads with the same code.
**Never manually edit `versionCode`** to a lower number — it must monotonically increase
across all tracks for the same applicationId.

## Dependency Rules

- Do not add new Gradle dependencies without noting them in `CHANGELOG.md`.
- Prefer AndroidX / KotlinX standard libraries over third-party for simple tasks.
- All new dependencies must be compatible with the Polyform Noncommercial 1.0.0 license.
- Google Play Services: use only the minimum submodules required (play-services-base,
  firebase-messaging). Do not add analytics, ads, or crashlytics SDKs.
- xterm.js is vendored (pinned version) in `composeApp/src/androidMain/assets/` and loaded
  via WebView — version bumps require manual integration test against a live session.

## Planning Rules

When creating a large implementation plan (3+ files or non-trivial architectural work):

1. **Create a plan document** in `docs/plans/` named `YYYY-MM-DD-<slug>.md`.
2. The plan must include:
   - **Date** (ISO 8601) at the top
   - **Version** at the time of planning (e.g. `v0.5.19`)
   - **Scope** — which modules/packages are affected
   - **Phases** — numbered steps in implementation order
   - **Status** — mark each phase as Planned / In Progress / Done as work proceeds
3. After implementation, update status and note the **version it shipped in**.

## Documentation Rules

Every commit that adds or changes behavior must update documentation. This is blocking
for merge and release.

### No internal tracker IDs in user-facing docs (inherited)

- B#, F#, BL# — internal only. Live in `docs/plans/README.md` and `docs/plans/*.md`.
- `README.md`, `CHANGELOG.md`, `docs/setup.md`, `docs/user-guide.md`, store listing,
  release notes use plain English.

### General documentation checklist (every change)

1. Update `CHANGELOG.md` under `[Unreleased]` (or current version).
2. Update `docs/config-reference.md` for any new settings.
3. Update `docs/operations.md` if the change affects release, signing, or distribution.
4. Update `README.md` if adding a new screen, surface, or user-visible feature.
5. Update the **documentation index** in `README.md` and `docs/README.md` for any new doc files.
6. Update `docs/testing-tracker.md` for any new surface or transport.

### New transport (`shared/src/commonMain/kotlin/.../transport/<name>/`)

1. Add full section to `docs/transports.md`: when to use, prerequisites, fallback behavior,
   limitations, security notes.
2. Add fields to `ServerProfile.kt`, `docs/config-reference.md`.
3. Update `docs/data-flow.md` with the sequence diagram for this transport.
4. Update Settings → Connections screen to expose the fields.

### New surface (Wear OS, Android Auto, widget, tile, complication)

1. Document in `docs/surfaces/<surface>.md`: target OS version, capabilities, limitations,
   interaction model, auth inheritance.
2. Add to `docs/testing-tracker.md` as a separate row.
3. Add to Play Store listing feature section if user-visible.

## Project Tracking

All bugs, plans, and backlog items in `docs/plans/README.md`. Same rules as parent:
permanent IDs, move to Completed when done, partially-fixed items annotated in place.

## Release vs Patch Discipline

User terminology determines the action:

- **"release"** = full Play Store release:
  1. Run all tests, increment version, update CHANGELOG with all changes since the last Play
     release, build **both** `com.dmzs.datawatchclient` (public) and
     `com.dmzs.datawatchclient.dev` (internal) AABs.
  2. Upload public AAB to the appropriate Play Console track (Internal → Closed → Open →
     Production). Never skip tracks — even trivial patches stage through Internal for 1 hour minimum.
  3. Upload internal AAB to Play Console Internal Testing **only** — never promote it.
  4. Create a GitHub release with tag `vX.Y.Z`, attach the public AAB + mapping file +
     SHA-256 sums, link to Play Store listing.
- **"commit"** / **"push"** / no keyword = commit & push only. Bump version (patch). Do not
  upload to Play Console.

### Required release artifacts per version

| Artifact | Purpose |
|----------|---------|
| `datawatch-client-X.Y.Z-release.aab` | Play Console public track upload |
| `datawatch-client-dev-X.Y.Z-release.aab` | Play Console internal track only |
| `datawatch-client-X.Y.Z-release.mapping.txt` | ProGuard mapping for crash symbolication |
| `datawatch-client-X.Y.Z-release.apk` | GitHub release asset for sideload |
| `SHA256SUMS` | Integrity verification |

### Pre-release security scan

Before every release run:

```bash
./gradlew detekt ktlint android-lint
./gradlew dependencyCheckAnalyze        # OWASP dependency check
./gradlew :composeApp:lintRelease
```

All HIGH severity findings must be fixed or documented with justification. New findings in
code you wrote MUST be addressed before release.

## Configuration Accessibility Rule

No setting may ever be hard-coded. Every user-configurable value MUST be reachable through:

1. **Settings UI** — field in the appropriate Settings screen section
2. **Config export** — `Settings → Export config` produces a JSON blob
3. **Config import** — pairs with export (round-trip verified in tests)
4. **Server echo** — settings stored server-side in datawatch's config round-trip via the
   REST API (not all mobile settings apply — device-local settings stay local)
5. **CLI deep link** — `adb shell am start -a com.dmzs.datawatchclient.SET_CONFIG` for test automation

### Release workflow (every version bump)

```bash
# 1. Bump versionName + versionCode in 3 files
#    composeApp/build.gradle.kts, wear/build.gradle.kts, shared/.../Version.kt

# 2. Commit, tag, push
git add -A && git commit -m "release: vX.Y.Z — description"
git tag -a vX.Y.Z -m "vX.Y.Z"
git push && git push --tags

# 3. Build both variants
./gradlew :composeApp:bundleRelease
./gradlew :composeApp:bundleDevRelease     # internal variant
./gradlew :wear:bundleRelease

# 4. Upload public AAB to Play Console Internal track via fastlane or manual
#    bundle release artifacts: AAB, mapping, APK, SHA256SUMS

# 5. Create GH release with binaries attached
gh release create vX.Y.Z \
  ./composeApp/build/outputs/bundle/release/composeApp-release.aab \
  ./composeApp/build/outputs/apk/release/composeApp-release.apk \
  ./composeApp/build/outputs/mapping/release/mapping.txt \
  ./SHA256SUMS \
  --title "vX.Y.Z" --notes-file /tmp/release-notes.md --verify-tag
```

### Common mistakes to avoid

- **Forgetting one of three Version locations** — CI grep check prevents this.
- **Uploading dev variant to public track** — applicationId mismatch blocks it, but always
  confirm before upload.
- **Creating a GH release without the mapping file** — crashes become un-symbolicated.

### Functional Change Checklist

After any functional change (new feature, bug fix, behavior change — not docs-only):

1. Bump version (patch minimum).
2. `./gradlew test connectedCheck`, `./gradlew detekt ktlint lintRelease`.
3. Live-test on at least one Android device + one Wear OS + Android Auto DHU (if the surface
   is affected).
4. Create release with all 5 artifacts above.
5. Verify Play Console internal track ingests and opens.

## Security Rules

- **Never commit internal hostnames, IP addresses, or Tailscale node names** — anything that
  identifies a specific user's infrastructure. This includes: short machine names / ring
  names, `*.ts.net` / `*.taila*.ts.net` Tailscale magic-DNS names, CIDR-specific IP
  addresses (LAN, CGNAT, or Tailscale ranges), custom internal-TLD hostnames
  (`*.internal`, `*.lab.*`, user-specific subdomains), and any URL derived from those.
  Docs, ADRs, plans, tests, CHANGELOG entries, commit messages, and source comments all
  apply. Use generic placeholders instead: `<your datawatch server>`, `host.example.com`,
  `datawatch.example`, `https://<host>:<port>`. Live-debug artifacts (logs, screenshots)
  are staging-only — scrub before they land in docs.
- Never log or commit API keys, tokens, bearer tokens, server URLs, or user identifiers.
- Never write code that sends data to services not in the allowlist (`docs/security-model.md`).
  Allowlist today: (1) user-configured datawatch servers, (2) Google FCM (push wake only),
  (3) Google Drive Auto Backup (encrypted DB).
- Never disable TLS hostname verification, even for self-signed cert users — use
  NetworkSecurityConfig trust anchors instead.
- `ServerProfile.token` must never appear in logs, crash reports, UI, analytics, or exports
  except via the explicit user-initiated copy-token action.
- xterm.js WebView runs in a restricted `WebChromeClient` with JS enabled but no
  `addJavascriptInterface` — only postMessage bridge to Kotlin host.

## Session & State Rules

- Never delete a cached session blob without user confirmation.
- Disconnection is a visible state with a banner and last-seen timestamp, never hidden.
- Never implement a background replay/queue worker (violates ADR-0013).

## Testing Requirements

When implementing any new feature or bug fix:

1. **Write tests** — Kotlin test files with close to 100% coverage for new/changed logic.
2. **Run all tests** — `./gradlew test connectedCheck` must pass before committing.
3. **Test all surfaces the feature touches**:
   - **Phone**: Compose UI test + live device
   - **Wear OS**: Wearable Compose test + live watch (or Wear emulator)
   - **Android Auto**: DHU (Desktop Head Unit) end-to-end
   - **Transport**: MockWebServer + live server
   - **Voice**: recording+whisper round-trip against live server
4. **Clean up test artifacts** — cached sessions, FCM tokens, test server profiles.
5. **Document** in `docs/plans/README.md` and `docs/testing-tracker.md`.

### Bug testing

Before closing any bug, document in `docs/testing.md` with: description, steps, expected,
actual (PASS/FAIL). UI-dependent fixes must include device/surface validation.

## Monitoring & Observability Rule (mobile-adapted)

Every new feature includes observability:

1. **Client metrics** — counters and timers exposed via `Settings → Diagnostics → Stats`,
   stored only locally in the encrypted DB.
2. **Server-side telemetry echo** — if the feature has an action that reaches the server,
   the existing datawatch `POST /api/telemetry` endpoint receives the event (closed loop).
3. **Wear complication + Auto status badge** — if user-facing numeric state exists, surface
   it on the watch complication (ADR-0028) and Auto messaging summary.
4. **Log files** — rolling encrypted log (SQLCipher journal) accessible via
   `Settings → Diagnostics → Export logs`. Tokens redacted.

## User Input Tracking During Active Work

When the user sends additional messages while actively working:

1. **Immediately note the input** — add to task tracking or create a sub-task.
2. **Do not ignore** — acknowledge and note when it will be handled.
3. **Update the plan** — add the new item as a task/ADR candidate.
4. **Design decisions** — always ask before proceeding (Decision Making rule).

## Rate Limit Handling (inherited from parent)

- If Claude hits an API rate limit or quota, **do not stop or fail**. Wait for reset and
  continue from where you left off.
- When paused, write a `PAUSED.md` in the project root with current context for clean resume.
- Signal the user about the pause and estimated resume time if known.

## Work Tracking (inherited)

Requests with more than one distinct task must show a plan checklist before starting and
update it as items complete:

```
## Plan
- [ ] Task 1
- [~] Task 2 (in progress)
- [ ] Task 3
```

Status markers: `[ ]` not started, `[~]` in progress, `[x]` completed. Single-task requests
skip this.

---

*These guardrails apply when Claude operates on this repository. They do not restrict what
datawatch sessions run from this app can do on the server side.*


# Memory & Knowledge (datawatch)

Use the datawatch memory system proactively during this session.

## Before starting work
- Use `memory_recall` to check if similar work has been done
- Use `kg_query` to understand entity relationships
- Use `research_sessions` for deep cross-session search

## During work
- Use `memory_remember` to save key decisions and patterns
- Use `kg_add` to record relationships

## When asked about project history
Always check memory first with `memory_recall` before answering from training data.

## Available tools
| Tool | Purpose |
|------|---------|
| `memory_recall` | Semantic search across project memories |
| `memory_remember` | Save decisions, patterns, context |
| `kg_query` | Entity relationship queries |
| `kg_add` | Record new relationships |
| `research_sessions` | Cross-session research |
| `copy_response` | Last LLM response from any session |
| `get_prompt` | Last user prompt from any session |