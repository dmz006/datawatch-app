# ADR-0002 — Android SDK Targets

## Status
Accepted

## Context
Minimum and target SDK values needed to be fixed before scaffold to gate feature availability and Play Store compatibility.

## Decision
`minSdk = 29` (Android 10), `targetSdk = 35` (Android 15).

## Consequences
Android 10+ APIs are available unconditionally, and the app declares full Android 15 compatibility for Play Store review.
