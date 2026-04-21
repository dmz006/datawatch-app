# PWA feature-parity status

> **Superseded 2026-04-22.** This document was a v0.2.1 snapshot of Sprint 1
> progress and is preserved here only as a redirect. The authoritative,
> continuously-maintained parity matrix lives in **[parity-plan.md](parity-plan.md)**.

## Where parity is tracked now

- **[parity-plan.md](parity-plan.md)** — every PWA feature × mobile
  status, grouped by PWA tab, updated each release.
- **[CHANGELOG.md](../CHANGELOG.md)** — per-release feature deltas.
- **[android-auto.md](android-auto.md)** — Auto-surface parity + known gaps.
- **[wear-os.md](wear-os.md)** — Wear-surface parity + known gaps.

## Why the redirect

The original matrix was written when Sessions-list was the only tab
shipping, with Sprints 2–6 enumerated as future work. Between v0.2.1
and v0.33.0 the mobile client closed 95%+ of PWA parity across 22
feature sprints (v0.11–v0.33). Maintaining two parallel matrices
(`parity-status.md` + `parity-plan.md`) caused one to rot every
release. `parity-plan.md` won.

v1.0.0 remains the release that closes the last ⏳ / 🚧 rows in
`parity-plan.md` (see its "Consolidated roadmap" section), per
ADR-0043.
