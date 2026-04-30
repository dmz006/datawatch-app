# ADR-0031 — Android Auto Dual-Track

## Status
Accepted

## Context
Android Auto's Driver Distraction Guidelines prohibit the full datawatch UI in a public Play Store release, requiring a separate internal build for passenger use.

## Decision
- **Public build** (`com.dmzs.datawatchclient`, Play Store) — Messaging template only, compliant with Google Driver Distraction Guidelines.
- **Internal build** (`com.dmzs.datawatchclient.dev`, Internal Testing only) — full passenger UI, voice-first, never promoted to public.
- Both installable simultaneously via distinct applicationIds, icons, FCM senders, and signing keys.

## Consequences
The public track passes Play policy review while the internal track provides a rich in-car experience for testing, with no risk of the unconstrained UI reaching the public Play Store.
