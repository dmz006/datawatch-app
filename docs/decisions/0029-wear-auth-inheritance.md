# ADR-0029 — Wear Auth Inheritance

## Status
Accepted

## Context
Wear OS needs a bearer token to reach the datawatch server, but a separate pairing or login flow on the watch would be impractical.

## Decision
Token pulled from the phone over Wearable Data Layer API. No separate pairing flow on the watch.

## Consequences
Auth on the watch is automatic once the phone is paired, with no additional user action required.
