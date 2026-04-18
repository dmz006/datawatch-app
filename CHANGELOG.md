# Changelog

All notable changes to this project will be documented in this file.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/) per
[AGENT.md Versioning rules](AGENT.md#versioning).

## [Unreleased]

### Changed
- App display name set to `datawatch` (lowercase) for all user-facing surfaces — Play
  Store listing, launcher label, Wear watchface, Android Auto, iOS bundle display
  (ADR-0041, supersedes name portion of ADR-0030). Dev variant reads `datawatch (dev)`.
  Technical identifiers (applicationId `com.dmzs.datawatchclient[.dev]`, Kotlin packages,
  repo name `dmz006/datawatch-app`, keystore file names) are unchanged.

## [0.1.0-pre] — 2026-04-18

### Added
- Complete design package under `docs/`: 18 documents covering architecture, data flow,
  data model, API parity, security, threat model, UX, Wear OS, Android Auto, branding,
  sprint plan, Play Store registration, privacy policy, and data safety.
- 40 Architecture Decision Records consolidated in `docs/decisions/README.md`, approved
  during the four-batch design Q&A on 2026-04-17.
- `AGENT.md` operating rules adapted from
  [dmz006/datawatch](https://github.com/dmz006/datawatch), Kotlin-stacked.
- KMP + Compose Multiplatform + Wear OS + Android Auto + iOS-skeleton Gradle scaffold.
- GitHub Actions CI skeleton (`ci`, `release`, `security`).
- Upstream cross-references to `dmz006/datawatch` issues #1, #2, #3.

### Security
- Polyform Noncommercial 1.0.0 license (matches parent project).
- SQLCipher + Android Keystore data-at-rest model documented.
- Closed-loop telemetry stance documented (no Crashlytics / Sentry / Firebase Analytics).

### Status
- Pre-MVP. Implementation begins Sprint 1 (2026-05-02). MVP target 2026-06-12; public
  production 2026-07-10.

[Unreleased]: https://github.com/dmz006/datawatch-app/compare/v0.1.0-pre...HEAD
[0.1.0-pre]: https://github.com/dmz006/datawatch-app/releases/tag/v0.1.0-pre
