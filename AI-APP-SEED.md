# AI-APP-SEED.md — datawatch-app Context Loader

**Last Updated:** 2026-05-25  
**Version:** 1.0.0  
**Maintainer:** dmz006  
**Purpose:** Comprehensive AI session context for the datawatch-app mobile client

---

## What This App Is

**datawatch-app** is the Android/Wear OS/Android Automotive (AAOS) companion client for the [dmz006/datawatch](https://github.com/dmz006/datawatch) daemon, which bridges AI coding sessions (Claude Code, Aider, etc.) to messaging platforms.

### Quick Facts

- **Type:** Kotlin Multiplatform (KMP) + Jetpack Compose + WebView
- **Platforms:** Android phone, Wear OS (smartwatch), Android Auto (in-vehicle)
- **Current Version:** 1.0.0 (Production/GA release)
- **Release Date:** 2026-05-21
- **License:** Polyform Noncommercial 1.0.0
- **Repository:** `github.com/dmz006/datawatch-app`
- **Parent:** `github.com/dmz006/datawatch` (the daemon/server)
- **Brand:** `datawatch` (lowercase, per ADR-0041)

### Core Responsibility

The app provides **real-time monitoring and interaction** with AI coding sessions running on one or more datawatch servers:
- View live session terminal output and chat messages
- Send replies and commands (keyboard input + voice transcription)
- Monitor process stats (CPU, memory, network) from eBPF observer
- Receive push notifications when sessions need input
- Switch between multiple remote servers (Tailscale, LAN, public)
- Run on foldable devices and tablets (2-pane side-by-side layout ≥600dp)

**What it does NOT do:**
- Run a local LLM or session agent (pure client architecture)
- Queue writes offline (fail-fast with visible errors)
- Collect analytics or telemetry (closed-loop per ADR-0024)
- Support multi-user or RBAC (single-user v1, abstract `Principal` for future)

---

## Critical Rules (AGENT.md)

These are **load-bearing invariants**. Violating them requires explicit user approval.

### Before Any Code Change

1. **Re-read the relevant section of AGENT.md** (planning, testing, versioning, docs).
2. **Verify compliance** with project identity and scope constraints.
3. **Flag conflicts** — if your planned approach breaks a rule, ask the user first.

### Architecture Invariants

| Invariant | Why | Impact |
|-----------|-----|--------|
| **Pure Client** | No server, no local LLM | Never add async tasks, background sync, or offline queues |
| **No Offline Queue** | Writes fail fast with visible errors | Use one-shot requests, no retry loops without user action |
| **Closed Loop** | No SaaS telemetry except FCM + Drive Auto Backup | No Crashlytics, Sentry, Google Analytics (except allowed) |
| **Single-User v1** | Interfaces abstract `Principal`, UI is single-role | Design for multi-user (in interfaces), ship single-user (in UI) |
| **Copy from PWA** | The web client (`dmz006/datawatch`) is the source of truth | Never invent mobile-only protocol; check parent first |

### Key Constraints

- **Scope:** Work only in `/home/dmz/workspace/datawatch-app/`
- **Dependencies:** KMP + Compose + WebView + xterm.js + SQLCipher
- **Play Store:** Minimal Google Play Services (firebase-messaging only; no crashlytics, ads, analytics)
- **Licensing:** All new dependencies must be Polyform NC 1.0.0 compatible

---

## Project Structure (Kotlin Multiplatform)

```
datawatch-app/
├── shared/                          # KMP common + platform-specific
│   ├── src/commonMain/              # Shared Kotlin (data, transport, VM)
│   │   ├── kotlin/.../data/         # Model classes (Session, Event, etc.)
│   │   ├── kotlin/.../transport/    # TransportClient, WebSocket, HTTP
│   │   ├── kotlin/.../vm/           # ViewModels (SharedFlow state)
│   │   └── kotlin/.../db/           # SQLDelight schemas
│   ├── src/androidMain/             # Android-specific code
│   └── src/iosMain/                 # iOS-specific code (not active in v1.0.0)
│
├── composeApp/                      # Android phone + tablet
│   ├── src/androidMain/kotlin/.../ui/
│   │   ├── sessions/                # SessionsScreen, SessionDetailScreen
│   │   ├── automata/                # PRD list, detail, execution
│   │   ├── alerts/                  # Alert list, detail
│   │   ├── observer/                # Process stats, heatmap, timeline
│   │   ├── dashboard/               # Multi-server aggregation
│   │   ├── settings/                # Server config, auth, preferences
│   │   └── common/                  # HeaderComponents, shared composables
│   ├── src/androidMain/assets/      # xterm.js (vendored), host.html
│   └── src/publicTrackRelease/      # Play Store public app
│   └── src/devRelease/              # Internal testing variant
│
├── wear/                            # Wear OS (smartwatch)
│   └── src/main/kotlin/.../ui/      # WearSessionsScreen, WearDetailScreen
│
├── auto/                            # Android Auto (AAOS, in-vehicle)
│   └── src/main/kotlin/.../car/     # CarAppService, vehicle UI
│
├── docs/
│   ├── README.md                    # Feature matrix, parity status
│   ├── setup.md                     # Build, SDK, signing
│   ├── config-reference.md          # Settings fields
│   ├── transports.md                # WebSocket, REST, Tailscale
│   ├── data-flow.md                 # Sequence diagrams
│   ├── testing-tracker.md           # Unit + live validation matrix
│   ├── operations.md                # Release, signing, Play Store
│   ├── implementation.md            # New features checklist
│   └── decisions/                   # ADRs (MADR format)
│
├── AGENT.md                         # Rules (SOURCE OF TRUTH)
├── CHANGELOG.md                     # Version history
├── README.md                        # Public landing
├── gradle.properties                # Version, build config
├── build.gradle.kts                 # Root build config
└── settings.gradle.kts              # Module includes: shared, composeApp, wear, auto
```

### Modules

| Module | Platform | Purpose | Status |
|--------|----------|---------|--------|
| `shared` | KMP (Android + iOS) | Models, transport, VM, DB | ✅ Core, stable |
| `composeApp` | Android phone/tablet | Main UI, terminal WebView | ✅ Feature-complete |
| `wear` | Wear OS | Watch client | ✅ Feature-complete |
| `auto` | Android Auto (AAOS) | In-vehicle UI | ✅ Feature-complete |
| `iosApp` | iOS | Not active in v1.0.0 | ⏸️ Deferred |

---

## Build & Release System

### Build Commands

```bash
# Full build (all modules, all checks)
./gradlew build

# Build just Android (phone + wear + auto)
./gradlew :composeApp:build :wear:build :auto:build

# Test (JUnit5, Robolectric, Turbine)
./gradlew test
./gradlew connectedAndroidTest

# Code quality checks (must pass before merge)
./gradlew detekt ktlint lintRelease

# OWASP dependency check (pre-release)
./gradlew dependencyCheckAnalyze

# Build release APK/AAB (signed, obfuscated)
./gradlew :composeApp:assemblePublicTrackRelease  # Phone APK
./gradlew :composeApp:bundlePublicTrackRelease    # Phone AAB (Play Store)

# RTK support (token optimization)
rtk ./gradlew build                 # Runs with token-optimized output
```

### Versioning (Three Places, Must Match)

Every version bump requires updating **all three**:

1. **`composeApp/build.gradle.kts`**: `versionName = "X.Y.Z"`, `versionCode = N` (monotonic)
2. **`wear/build.gradle.kts`**: same `versionName`, same `versionCode`
3. **`shared/src/commonMain/kotlin/.../Version.kt`**: `const val VERSION = "X.Y.Z"`

**Rules:**
- Major (1.0.0) reserved for PWA parity milestone (ADR-0043) — not for other reasons
- Minor (e.g., 0.8.0) for new features
- Patch (e.g., 0.7.5) for bug fixes, docs, refactors
- `versionCode` increments every release; **never reuse**; **never decrement**
- CI enforces parity check across all three

### Release Process (Full Play Store Release)

```bash
# 1. Test & verify
./gradlew clean build test
./gradlew detekt ktlint lintRelease

# 2. Bump version (all three places)
# Edit composeApp/build.gradle.kts, wear/build.gradle.kts, Version.kt

# 3. Update CHANGELOG.md (under [Unreleased] → [X.Y.Z])

# 4. Commit & tag
git add -A
git commit -m "chore(version): bump to vX.Y.Z"
git tag vX.Y.Z

# 5. Build signed AABs + APK + mapping
./gradlew :composeApp:bundlePublicTrackRelease
./gradlew :composeApp:bundleDevRelease          # Internal test variant
./gradlew :composeApp:assemblePublicTrackRelease

# 6. Verify artifacts
ls -la composeApp/build/outputs/bundle/publicTrackRelease/
ls -la composeApp/build/outputs/apk/publicTrack/release/
ls -la composeApp/build/outputs/mapping/publicTrackRelease/

# 7. Upload to Play Console (tracks: Internal → Closed → Open → Production)

# 8. Create GitHub release with artifacts + SHA256SUMS
gh release create vX.Y.Z *.aab *.apk mapping.txt SHA256SUMS
```

**Required Artifacts:**
- `datawatch-client-X.Y.Z-release.aab` (public Play Store)
- `datawatch-client-dev-X.Y.Z-release.aab` (internal testing)
- `datawatch-client-X.Y.Z-release.apk` (sideload + GitHub)
- `mapping.txt` (ProGuard symbol table)
- `SHA256SUMS` (integrity verification)

### RTK Integration (Token Optimization)

The project supports `rtk` (Rust Token Killer) for token-optimized builds:

```bash
rtk ./gradlew build                # Token-optimized
rtk ./gradlew test                 # Saves 90-99% tokens on tests
rtk ./gradlew detekt ktlint        # Saves 59-80% tokens on linting
rtk git status && rtk git diff     # Saves 59-80% tokens on git operations
```

**RTK is transparent:** if a tool doesn't have a token filter, it passes through unchanged.
Always prefix build commands with `rtk` for optimal token usage.

---

## Testing Strategy

### JVM Unit Tests (Local)

**Framework:** JUnit5, Turbine (Flow testing), Robolectric (Android-unaware), MockWebServer (HTTP)

```bash
./gradlew test                          # Run all unit tests
./gradlew :shared:test                  # Just shared module
./gradlew test --tests "*SessionVMTest" # Single test class
```

**Coverage Target:** ~90-100% on new/changed logic. Tests must be functional, not skeletons.

**Key Test Utilities:**
- `Turbine` — Flow/StateFlow assertions
- `Robolectric` — Android framework without device
- `MockWebServer` — HTTP mock for transport
- `SQLDelight TestDriver` — In-memory DB

### Live Device Validation

**Framework:** Manual + Maestro (E2E, deferred to v1.1)

**Matrix:** `docs/testing-tracker.md`
- Row per surface: Sessions (phone/tablet), Automata (phone), Alerts, Observer, Dashboard, Settings, Monitor, Wear, AAOS
- Columns: Tested (unit tests exist & pass), Validated (live device confirmed)
- **Rule:** Never mark Validated=Yes from unit tests alone — require a real device + real server

**Validation Checklist:**
- Real datawatch server running (v8.6.1+)
- Real Android device (or high-fidelity emulator)
- Test transport: Tailscale, LAN, or `localhost` with USB reverse-proxy
- Capture device model, Android version, server version
- Document observed behavior (screenshots, video if needed)

### Before Every Release

```bash
# Security scan
./gradlew detekt ktlint lintRelease
./gradlew dependencyCheckAnalyze

# All HIGH severity findings must be fixed or documented with justification
# New findings in your code MUST be addressed before release
```

---

## Datawatch MCP & Tooling

### MCP Server Connection

The session has access to `datawatch-johnnyjohnny-630b` MCP server for:
- Agent spawning and monitoring
- Memory (episodic KG) persistence
- Session/PRD/automaton management
- Cost tracking and analytics

**Key Tools Available:**
- `memory_recall(query)` — Semantic search project memories
- `memory_remember(text)` — Save learnings
- `autonomous_prd_*` — Automata/PRD operations
- `session_*` — Session info, rollback, guardrails
- `cost_summary()` — Token usage
- `audit_query()` — Operation audit trail

### Memory System (Episodic KG)

Located in `/home/dmz/.claude/projects/-home-dmz-workspace-datawatch-app/memory/`

**Load Project Memories Before Starting Work:**

```bash
# Query for recent changes and learnings
memory_recall("what was recently worked on in terminal scrolling")
memory_recall("what didn't work in layout changes")
memory_recall("session detail screen fixes")
memory_recall("keyboard interaction issues")

# Review project status
memory_list(project_dir="/home/dmz/workspace/datawatch-app")
```

**Memory Types Used in This Project:**
- `user` — Developer role/preferences
- `feedback` — What approaches worked/failed (critical for iterations)
- `project` — Ongoing initiatives, blockers, decisions
- `reference` — External resources (server URLs, docs links)

**Current Key Memories (as of 2026-05-22):**
1. v1.0.0 UI/UX completion (all features done)
2. Terminal scrolling actively being fixed (may or may not be fully solved)
3. Test state: 261/379 pass, 36 skip, 0 blocked
4. Test infrastructure constraints (secondary instance only, no repo edits)
5. PWA-parity header standardization completed

---

## Common Tasks & Patterns

### Adding a New Feature

1. **Plan** → Create `docs/plans/YYYY-MM-DD-<slug>.md` (MADR format)
2. **Implement** → Update code in appropriate module
3. **Test** → Add JUnit5 tests; target ~90% coverage
4. **Document** → Update `CHANGELOG.md`, `docs/`, `README.md`
5. **Validate** → Test on real device if user-visible
6. **Release** → Version bump (minor), Play Store upload

### Fixing a Bug

1. **Reproduce** → Add failing unit test or live validation steps
2. **Fix** → Update code
3. **Test** → Verify test passes; run full suite (`./gradlew test`)
4. **Document** → `CHANGELOG.md` entry (patch bump)
5. **Commit** → Single meaningful commit with test

### Adding a New Dependency

1. **Check License** → Must be Polyform NC 1.0.0 compatible
2. **Prefer AndroidX/KotlinX** — Only third-party if strictly needed
3. **Avoid Google Play Services** — Use minimal submodules (firebase-messaging only)
4. **Update CHANGELOG.md** — Document why and what version
5. **Test** → `./gradlew build detekt` must pass

### Updating xterm.js

xterm.js is **vendored** (pinned version) in `composeApp/src/androidMain/assets/`.

1. **Manual process** (not automated)
2. **Integration test required** — Must test live against real session
3. **Update asset files** → `host.html`, xterm library files
4. **Document** → Note version, date, breaking changes in CHANGELOG.md
5. **Commit** → Single commit with vendor files

---

## Session Context Loading (For AI Agents)

**Before starting ANY work on this codebase, load this context:**

### Step 1: Load Project Memories

```bash
memory_recall("terminal scrolling")
memory_recall("UI fixes v1.0.0")
memory_recall("what didn't work")
memory_recall("keyboard interaction")
```

### Step 2: Review Current State

```bash
# Version check
cat shared/src/commonMain/kotlin/com/dmzs/datawatchclient/Version.kt
cat gradle.properties | grep DATAWATCH_APP_VERSION

# Recent commits
git log --oneline -20

# Build status
./gradlew clean build detekt ktlint 2>&1 | head -30
```

### Step 3: Understand Task Context

- **If UI/Layout:** Read AGENT.md section on "Copy from PWA first"
- **If Terminal:** Check recent memory for scrolling work, read SessionDetailScreen.kt (terminal section)
- **If Transport:** Check transports.md, data-flow.md
- **If Test:** Review testing-tracker.md, understand test infra constraints
- **If Release:** Follow release checklist in operations.md

### Step 4: Document What You Learn

After diagnosing or implementing:

```bash
memory_remember("Found that TerminalView + verticalScroll causes blank screen due to WebView measurement conflict")
memory_remember("Column wrapper with weight(1f) better for flex layout than direct TerminalView weight")
memory_remember("What works: Box(weight=1f) containing TerminalView(fillMaxWidth) allows WebView internal scroll")
```

---

## Key Files & Their Purposes

| File | Purpose | Who Edits | When |
|------|---------|-----------|------|
| `AGENT.md` | **Rules & constraints** (source of truth) | User | When rules change |
| `CHANGELOG.md` | Version history & release notes | AI + User | Every commit |
| `gradle.properties` | Version + build config | AI | Version bumps |
| `shared/src/commonMain/kotlin/.../Version.kt` | Shared version const | AI | Version bumps |
| `composeApp/build.gradle.kts` | Phone/tablet build | AI (deps only) | Rare |
| `docs/testing-tracker.md` | Unit + live test matrix | AI + User | After validation |
| `docs/plans/*.md` | Feature plans (MADR) | AI + User | Before big features |
| `SessionDetailScreen.kt` | Terminal UI (active work area) | AI | Bug fixes, scrolling |
| `TransportClient.kt` | WebSocket/HTTP transport | AI (rare) | New transports |
| `Session*.kt` (data models) | Session/Event/PRD models | AI (rare) | Protocol changes |

---

## Known Issues & Workarounds

### xterm / WebView Architecture (Settled 2026-05-25, builds 252–283)

**Status:** Working. Live tail visible on session open AND when keyboard is up. ~30 builds of failed iterations until root cause was found.

**The five interlocking bugs** (in the order they were exposed, not the order they were fixed):

1. **DPR scaling mismatch (root cause of "stuck above live tail")**
   - `Modifier.onSizeChanged` reports the AndroidView size in **device pixels** (e.g. 1845 on the S24 Ultra).
   - `container.style.height = h + 'px'` in CSS treats the value as **CSS pixels**.
   - At DPR=3.75 (Samsung 600 dpi), 1845 CSS px = 6919 device px → xterm container 3.75× larger than the visible body area → bottom 2/3 (including cursor row and chrome footer) clipped offscreen by `body { overflow: hidden }`.
   - Fix: `container.style.height = (h / window.devicePixelRatio) + 'px'` in `dwExplicitSize`.
   - **Diagnose**: log `DPR`, `winW/H`, `body.W/H`. If `winW > body.offsetWidth` significantly, you're hitting this.

2. **xterm `term.write` is async; concurrent writes stomp each other**
   - With 30 fps pane_capture dispatch from Kotlin and ~50 ms parse time per 15 KB write, `term.write` calls queue up. Calling `term.reset()` between calls cancels the previous in-flight write mid-process, leaving partial data in the buffer (top rows from N writes ago, cursor row from latest).
   - Fix: serialize via the `term.write(joined, callback)` callback. Keep one `_pendingCap` and only flush after the prior `_writeInFlight` clears.
   - **Diagnose**: log `buf.first` / `buf.last` AFTER the write callback fires; if they disagree with the just-written `pane.first` / `pane.last`, writes are racing.

3. **`enableEdgeToEdge` is required to make `imePadding` work cleanly**
   - Without `enableEdgeToEdge` in `MainActivity.onCreate`, Android's IME handling is inconsistent: sometimes adjustResizes the window, sometimes adjustPans, sometimes does nothing (depends on focus state, theme defaults, manifest, target SDK). `WindowInsets.ime` always reports > 0 either way, so `Modifier.imePadding()` double-counts the keyboard height on top of whatever the system did — produces a huge black gap on the S24.
   - Even explicit `android:windowSoftInputMode="adjustResize"` in the manifest didn't reliably trigger the resize.
   - Fix: call `enableEdgeToEdge()` in `MainActivity.onCreate`, then apply `Modifier.navigationBarsPadding().imePadding()` ONCE at the outermost Column. Compose becomes the sole owner of inset handling.

4. **`DATAWATCH_COMPLETE:` filter dropped every pane_capture**
   - Mobile had the original v0.x "break on any line containing the marker" behavior. PWA fixed this years ago at `app.js:609` to *filter* matching lines and keep the rest. Mobile never got the fix. Any session that ever ran a bash command mentioning the literal string (e.g. `grep "DATAWATCH_COMPLETE:"`) had every subsequent pane_capture silently skipped, freezing the terminal forever.
   - Fix: `lines.filter(l => !l.includes('DATAWATCH_COMPLETE:'))` instead of `for-break`.
   - **Diagnose**: instrument `dwPaneCapture` entry/exit counters. If exits show `dwcomplete: N` matching enter count, this is it.

5. **`setMinSize(_, 40)` forces row count regardless of viewport**
   - The claude-code 120×40 minimum was originally to keep the TUI laid out correctly. The 40-rows minimum makes sense for cols (TUIs need width), but with the keyboard open the visible WebView fits maybe 15 rows on phone; forcing 40 makes xterm render content TALLER than the viewport → live tail clipped.
   - Fix: `setMinSize(120, 0)` — enforce cols only.

**Required architecture for any future xterm/IME work:**

- **Compose layout** — `Scaffold(contentWindowInsets=WindowInsets(0))` → outer `Column(.fillMaxSize().navigationBarsPadding().imePadding())`. Inside: header (natural height), `Column.weight(1f).fillMaxWidth()` containing `TerminalView.weight(1f).fillMaxWidth()`, then the composer. NO additional `imePadding`/`navigationBarsPadding` lower in the tree.
- **MainActivity** — must call `enableEdgeToEdge()` in `onCreate`.
- **TerminalView** — `onSizeChanged` callback dispatches `dwExplicitSize(w, h)`; do NOT cap the height (the 800 px cap was an early dead-end). The WebView is a `TerminalWebView` subclass that blocks vertical scroll via `overScrollBy`, `scrollTo`, `scrollBy`, `computeVerticalScrollRange` overrides (Android WebView synthesizes scrollY changes from touch drags even with `body { overflow: hidden }`).
- **host.html** — `body { height: 100%; touch-action: none }` (no `100dvh`). `dwExplicitSize(w, h)` divides by `window.devicePixelRatio` before setting CSS dimensions. `safeFit()` is pure measurement + fit + unconditional `scrollToBottom` (unless `_scrollMode`); no `wasAtBottom` snapshots (racy). `dwPaneCapture` serialized via `_pendingCap` / `_writeInFlight` / write callback. Filter (don't skip-on-match) `DATAWATCH_COMPLETE:` lines. Strip trailing whitespace-only rows. Use `\x1b[2J\x1b[3J\x1b[H` for clear (not `term.reset()`, which causes a visible flash on every redraw).

**Reference**: PWA's `app.js` lines ~3175-3230 (initXterm + visualViewport handler), ~609-680 (pane_capture write path with atBottom check + DATAWATCH_COMPLETE filter). Mobile mirrors the PWA's logic for input handling, scroll behavior, and protocol semantics — the only intentional divergence is mobile uses `_scrollMode` flag from the toolbar PgUp/PgDn buttons (no touch scrollback) where PWA uses `buf.viewportY >= buf.baseY` (browsers allow touch scroll of `.xterm-viewport`).

**Known limitation**: the visible bottom of the WebView may briefly show 1-2 padded empty rows on session open when pane_capture has fewer rows than `term.rows`. Acceptable trade-off; live tail still visible.

### Test Infrastructure

**Status:** Secondary instance only, 261/379 tests pass

**Constraints:**
- Test server at secondary host (never production)
- No repo edits to test config
- File tickets for server issues
- Re-enabled sprints pending re-run (scheduled 2026-05-16)

**E2E Testing:**
- Maestro layer deferred to v1.1
- Only JVM unit tests today
- Live validation on real devices only

---

## Useful Links & References

### Internal Docs
- `README.md` — Feature overview, parity matrix
- `docs/setup.md` — Build environment, SDK, signing keys
- `docs/config-reference.md` — All settings fields
- `docs/transports.md` — Transport selection, prerequisites
- `docs/data-flow.md` — Sequence diagrams for major flows
- `docs/testing-tracker.md` — Which features are tested/validated
- `docs/operations.md` — Build, signing, Play Store upload

### Parent Project (Source of Truth)
- `https://github.com/dmz006/datawatch` — daemon, server, PWA
- `dmz006/datawatch` `AGENT.md` — parent rules (mobile inherits + strengthens)
- `app.js`, `index.html`, `openapi.yaml` — protocol source of truth

### Technologies
- **Kotlin Multiplatform** — shared code, platform-specific implementations
- **Jetpack Compose** — declarative UI framework
- **WebView + xterm.js** — terminal rendering
- **SQLDelight** — compile-time safe DB access
- **Turbine** — Flow/StateFlow testing
- **Robolectric** — Android unit tests without device

---

## Checklist: Ready to Work?

Before editing any code:

- [ ] Re-read `AGENT.md` section relevant to your task
- [ ] Load project memories (`memory_recall` for recent work, issues, learnings)
- [ ] Verify build is clean: `./gradlew clean build detekt ktlint`
- [ ] Check current version: `grep VERSION shared/.../Version.kt`
- [ ] Review task — does it violate any invariants? (ask user if unsure)
- [ ] Plan for test coverage (~90% on new code)
- [ ] Plan for documentation updates (CHANGELOG, docs/, README)
- [ ] Identify which module(s) you're editing: shared, composeApp, wear, auto?

---

## Summary

This seed file provides complete context for AI coding sessions on datawatch-app:

1. **What the app is** — Android/Wear/AAOS client for datawatch daemon
2. **Non-negotiable rules** — AGENT.md (pure client, closed loop, copy from PWA, etc.)
3. **Project structure** — KMP modules, build system, file purposes
4. **Build & release** — Versioning (3 places), release checklist, RTK support
5. **Testing** — JVM unit tests + live validation matrix
6. **Memory loading** — How to query project learnings before working
7. **Common tasks** — Patterns for features, bugs, dependencies, docs
8. **Known issues** — Terminal scrolling status, test infrastructure constraints
9. **References** — Links to parent, docs, technologies

**Every AI session should:**
1. Load this file first
2. Query project memories
3. Verify build/version state
4. Check AGENT.md rules for the specific task
5. Update memories when done (what worked, what didn't)

**Next Step:** Integrate AI-APP-SEED.md loading into pre-execution hooks and memory queries.

---

**Document Version:** 1.0  
**Last Updated:** 2026-05-22  
**Maintained By:** AI-aware development team with Claude Code
