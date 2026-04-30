# ADR-0010 — TLS / Certs

## Status
Accepted

## Context
Users operating self-hosted datawatch servers commonly use self-signed certificates, so the app needed a flexible TLS trust model.

## Decision
NetworkSecurityConfig per-profile trust anchors; self-signed certificates supported via user-confirmed CA import; cert pinning opt-in per server.

## Consequences
Each server profile can carry its own trust configuration, enabling self-signed and pinned certs without relaxing global TLS policy.
