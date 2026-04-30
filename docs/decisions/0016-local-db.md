# ADR-0016 — Local DB

## Status
Accepted

## Context
A local persistence layer was needed for caching server data that meets the project's security and cross-platform requirements.

## Decision
SQLDelight over SQLCipher-encrypted SQLite. Master key held in Android Keystore.

## Consequences
All locally cached data is encrypted at rest, and the master key is hardware-backed on supported devices.
