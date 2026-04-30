# ADR-0003 — Repo + Signing

## Status
Accepted

## Context
Repository hosting, licence, CI provider, and release-signing strategy needed to be locked before any code was pushed.

## Decision
Repository at `github.com/dmz006/datawatch-app`, public, licensed under Polyform NC 1.0.0; CI via GitHub Actions; release signing via Google Play App Signing.

## Consequences
Google holds the upload key for production artefacts, simplifying key rotation and Play Store integrity checks.
