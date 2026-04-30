# ADR-0017 — Backup

## Status
Accepted

## Context
Android Auto Backup needed to be configured before release to define what is included and how credentials are handled on restore.

## Decision
Android Auto Backup to Google Drive enabled. Encrypted DB + non-secret prefs included; Keystore material re-binds on restore (user re-enters token).

## Consequences
Users can restore app state after a device change, but must re-enter their bearer token because Keystore keys are not portable across devices.
