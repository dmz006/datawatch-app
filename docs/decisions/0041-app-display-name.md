# ADR-0041 — App Display Name

## Status
Accepted

## Context
The initial app display name "Datawatch Client" (from ADR-0030) did not align with the parent project's lowercase `datawatch` brand.

## Decision
App display name is `datawatch` (lowercase). Supersedes the name portion of ADR-0030. Play Store listing, launcher icon label, Wear watchface, Android Auto surface, iOS bundle display, and in-app word mark all read `datawatch`. The dev build reads `datawatch (dev)`. Technical identifiers (Kotlin packages, applicationId `com.dmzs.datawatchclient[.dev]`, GitHub repo name, keystore file names) are unchanged. In prose docs, "datawatch mobile client" or "the datawatch mobile app" is used when disambiguation from the parent server daemon is needed.

## Consequences
All user-facing surfaces are aligned with the parent brand; technical identifiers are unaffected, avoiding expensive renaming busywork.
