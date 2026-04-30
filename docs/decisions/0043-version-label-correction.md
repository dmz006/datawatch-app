# ADR-0043 — Version Label Correction

## Status
Accepted

## Context
The v1.0.0 tag applied to the ADR-0042 scope-close commit was mislabelled; the 1.0.0 milestone should be reserved for full PWA parity.

## Decision
1.0.0 is reserved for the release that reaches 100% client-side parity with the PWA at [dmz006/datawatch](https://github.com/dmz006/datawatch/) — i.e. every row in `docs/parity-status.md` flipped to checked. The earlier v1.0.0 / v1.0.1 tags are renumbered to v0.10.0 / v0.10.1 (same commits, same artefacts). The v1.1 → v1.4 roadmap in `docs/parity-plan.md` is renumbered v0.11 → v0.14 accordingly. ADR-0042 keeps its original text but every "v1.0.0" reference in that ADR now reads v0.10.0. No functional change to scope, timeline, or release artefacts — this is a labelling correction only.

## Consequences
Version numbering now accurately signals maturity: v0.x releases are pre-parity builds, and v1.0.0 is a meaningful milestone that signals complete PWA feature parity.
