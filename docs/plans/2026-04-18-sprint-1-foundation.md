# Sprint 1 — Shared foundation: transport, storage, onboarding

- **Date started:** 2026-04-18
- **Version at plan time:** v0.1.0-pre
- **Target completion:** 2026-05-15 (end of Sprint 1)
- **Target ship version:** v0.2.0 (Play Console Internal Testing track)
- **Feature IDs:** F1 (promoted from `docs/plans/README.md`)

## Scope

The `:shared` KMP module goes from empty-ish placeholders to a working data + network
spine that `:composeApp` can consume to render real server + session data. A single
Android phone build can add a server profile, authenticate, and list sessions against
a live datawatch instance.

Touched modules:
- `shared/src/commonMain` — domain, transport, storage, use cases
- `shared/src/androidMain` — SQLCipher driver, Keystore master key, Ktor OkHttp engine
- `shared/src/commonTest` — unit tests
- `composeApp` — onboarding screen, add-server, Sessions tab, bottom-nav shell

Out of scope (explicitly — later sprints handle these):
- WebSocket `/ws` streaming (Sprint 2)
- xterm terminal (Sprint 2)
- FCM push (Sprint 2)
- MCP SSE (Sprint 3)
- Voice (Sprint 3)
- All-servers fan-out (Sprint 3)
- Intent-relay fallback (Sprint 3)
- Wear OS + Android Auto (Sprint 4)

## Phases

### Phase 1 — Shared domain + transport plumbing (this commit)

**Status:** In progress.

- [x] `domain/Session.kt`, `domain/SessionState.kt`, `domain/Principal.kt`,
      `domain/Prompt.kt`, `domain/ReachabilityProfile.kt`
- [x] `transport/TransportClient.kt` — expanded interface (health, sessions,
      session operations)
- [x] `transport/TransportError.kt` — typed error hierarchy
- [x] `transport/dto/*.kt` — wire types aligned to datawatch `openapi.yaml`
- [x] `transport/rest/RestTransport.kt` — Ktor-based implementation
- [x] SQLDelight schema expansion (session, session_message, settings_kv, migrations)
- [x] `storage/ServerProfileRepository.kt` + one unit test

### Phase 2 — Android storage + crypto (next commit)

**Status:** Planned.

- [ ] `androidMain/storage/AndroidDatabaseFactory.kt` — SQLCipher driver bootstrap
- [ ] `androidMain/security/KeystoreManager.kt` — master-key generation + unwrap
- [ ] `androidMain/security/TokenVault.kt` — EncryptedSharedPreferences wrapper for
      bearer tokens (stored behind keystore alias)
- [ ] `commonMain/storage/DatabaseFactory.kt` — `expect` side
- [ ] `commonTest/` — token-storage round-trip test (Android instrumented)
- [ ] `androidTest/` — SQLCipher open/close and key-rotation test

### Phase 3 — composeApp onboarding + sessions list

**Status:** Planned.

- [ ] Onboarding: welcome → add-server → health-check spinner → enter bottom-nav
- [ ] `SettingsServerViewModel` — add/remove/edit server profile
- [ ] `SessionsViewModel` — observe + refresh session list for active server
- [ ] `SessionsScreen` composable — scrollable list with state pills
- [ ] `AddServerScreen` composable — URL + token + reachability profile + trust-anchor
- [ ] Online/offline banner (ADR-0013)
- [ ] Bottom-nav shell (Sessions / Channels / Stats / Settings)
- [ ] Deep-link router for `dwclient://server/<id>`

### Phase 4 — Test + release (end of sprint)

**Status:** Planned.

- [ ] MockWebServer `RestTransportLiveTest` — 200 / 401 / 5xx / network-error paths
- [ ] Compose UI test: add-server → sessions list happy path
- [ ] `docs/testing-tracker.md` updated with Tested=Yes rows
- [ ] `docs/implementation.md` updated with transport + storage entries
- [ ] Version bump: 0.1.0-pre → 0.2.0, CHANGELOG entry, tag `v0.2.0`
- [ ] Play Console Internal Testing upload (public AAB; dev AAB optional)

## Parity cross-references

- ADR-0004 — direct API first, Intent-relay fallback is later sprint
- ADR-0008 — pure client, no local server/agent
- ADR-0010 — NetworkSecurityConfig trust anchors
- ADR-0011 — static bearer token, single-user, Principal abstraction ready
- ADR-0013 — no offline queue (fail-fast + user retry)
- ADR-0016 — SQLDelight + SQLCipher
- ADR-0019 — raw-YAML config editor stays disabled (not exposed this sprint)

## Risks

- **JDK incompatibility on dev box:** local JDK is 25; AGP 8.5.2 ceiling is 21. User
  installs JDK 21 (SDKMAN recommended) before building locally. CI uses JDK 21 already.
- **dmz006/datawatch#1 not merged yet:** if push/device-register endpoint isn't
  available by Sprint 2, mobile pivots to ntfy-only subscription.
- **SQLCipher Android module Gradle integration:** needs careful version alignment
  with NDK; fallback is `requery/sqlcipher-android` older release if current breaks.

## Exit criteria

- Fresh `./gradlew :shared:build` passes on a clean clone with JDK 21.
- Android debug build installs on a phone and shows a placeholder "add your first
  datawatch server" UI.
- Entering a working bearer URL + token returns a session list drawn from the live
  datawatch server.
- All new code has unit tests; `docs/testing-tracker.md` is updated.
