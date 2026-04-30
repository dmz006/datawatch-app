# ADR-0019 — Flagged Parity Exclusions

## Status
Accepted

## Context
Several server capabilities were identified as inappropriate or impractical to expose fully on a mobile client, requiring explicit user sign-off on each exclusion.

## Decision
The following server features are excluded or restricted on mobile:
- eBPF network tracking — view only
- GPU / CPU / memory stats — view only
- `kill-orphans` — confirmation dialog, no biometric gate
- Editing server token / crypto keys — blocked on mobile
- Session pipelines / DAGs editor — read-only on phone, editable in PWA
- Raw YAML config editor — disabled; structured form only

## Consequences
These six exclusions are formally documented; any future promotion to full mobile support requires a superseding ADR.
