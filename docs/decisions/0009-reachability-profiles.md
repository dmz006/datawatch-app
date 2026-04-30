# ADR-0009 — Reachability Profiles

## Status
Accepted

## Context
Users run datawatch servers in varied network environments, requiring the mobile client to support multiple connectivity strategies without mandating cloud exposure.

## Decision
Supported profiles: Tailscale, local Wi-Fi LAN, DNS TXT covert channel, and messaging-backend relay via Intent handoff. No Cloudflare. No public HTTPS exposure required.

## Consequences
Users can reach their server from any supported network topology; the burden of connectivity configuration remains with the user rather than the app.
