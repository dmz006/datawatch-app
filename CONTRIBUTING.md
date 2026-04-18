# Contributing

Solo development today (dmz + Claude). Contributions are welcome, but please read the
[AGENT.md](AGENT.md) before opening an issue or pull request — the decision-making,
security, and release rules apply to all contributors (human and AI).

## Getting started

1. Fork + clone.
2. Bootstrap the Gradle wrapper: `gradle wrapper --gradle-version 8.9` (pre-MVP scaffold
   does not yet commit the wrapper JAR; this is a one-time step).
3. `./gradlew build` — expect red until Sprint 1 lands. Until then, this repo is a design
   + scaffold snapshot.

## Before you open a PR

- ✅ `./gradlew detekt ktlintCheck android-lintDebug` passes.
- ✅ `./gradlew test` passes.
- ✅ Version bumped per [AGENT.md Versioning](AGENT.md#versioning).
- ✅ `CHANGELOG.md` updated under `[Unreleased]`.
- ✅ If you added a new setting / surface / transport, docs were updated too.
- ✅ ADR added if you made an architectural decision not covered by existing ADRs.

## Issue labels

- `bug` — something is broken
- `feature` — new capability
- `design` — needs an ADR before implementation
- `parity` — tracks an upstream datawatch change needed for mobile
- `surface/wear` · `surface/auto` · `surface/phone` · `surface/shared`
- `good-first-issue` — small, isolated, well-documented

## Communication

- GitHub issues: user-visible bugs and feature requests.
- GitHub discussions (if enabled): broader design conversation.
- Security issues: see [SECURITY.md](SECURITY.md) — private disclosure first.

## Code of conduct

Be kind. Work in good faith. Anyone caught harassing others is done.
