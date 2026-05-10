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

### Phase 2 — Android storage + crypto

**Status:** Done.

- [x] `androidMain/storage/AndroidDatabaseFactory.kt` — SQLCipher-wrapped driver
- [x] `androidMain/security/KeystoreManager.kt` — AES-256-GCM master key + HMAC
      derivation for DB passphrase; StrongBox-preferred
- [x] `androidMain/security/TokenVault.kt` — EncryptedSharedPreferences (AES-256-GCM
      values, AES-256-SIV keys) with MasterKey-bound storage; alias-keyed by
      profile id
- [x] `commonMain/storage/DatabaseFactory.kt` — `expect` side already landed in Phase 1
- [x] `DatawatchApp.onCreate` loads `libsqlcipher.so` before any DB open attempt
- [ ] `androidTest/` — SQLCipher open/close + Keystore round-trip test (deferred to
      Phase 4 because it needs an instrumented Android test runner; unit coverage
      on pure-logic callers is sufficient for now)

### Phase 3 — composeApp onboarding + sessions list

**Status:** Done (core flow shipped).

- [x] Onboarding: welcome → add-server → health-check probe → home shell
- [x] `AddServerViewModel` — form state + live probe + vault write on success + roll-back on probe failure
- [x] `SessionsViewModel` — observe + refresh session list for active server; disconnect banner (ADR-0013)
- [x] `SessionsScreen` composable — list with state pills, refresh action, stale-banner on disconnect
- [x] `AddServerScreen` composable — display name + URL + token + self-signed toggle
- [x] Bottom-nav shell (Sessions / Channels / Stats / Settings) with Material 3 `NavigationBar`
- [x] `ServiceLocator` hand-wired DI for singletons (DB, repos, token vault, HttpClient)
- [x] `createHttpClient()` expect/actual for Android (OkHttp) + iOS (Darwin)
- [ ] Deep-link router for `dwclient://server/<id>` — deferred to Sprint 2 with the rest of the URI handling
- [ ] Cert-pinning opt-in UI — deferred to Sprint 2; trust-anchor input stays on the add-server screen as a toggle only for now

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
