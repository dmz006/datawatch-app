# ADR-0031 — Android Auto Dual-Track

## Status
Superseded by ADR-0049 (2026-09-15). Category is now MESSAGING (see ADR-0049 for full rationale).

## Context
Android Auto's Driver Distraction Guidelines prohibit the full datawatch UI in a public Play Store release, requiring a separate internal build for passenger use.

## Decision
- **Public build** (`com.dmzs.datawatchclient`, Play Store) — `category.OTHER`, compliant with Google Driver Distraction Guidelines. Uses `ListTemplate`, `PaneTemplate`, `MessageTemplate` (session detail), and `NavigationTemplate`-free layout. TTS and voice reply delivered via `CarAppExtender` notification actions.
- **Internal build** (`com.dmzs.datawatchclient.dev`, Internal Testing only) — full passenger UI, voice-first, never promoted to public.
- Both installable simultaneously via distinct applicationIds, icons, FCM senders, and signing keys.

## Category revision — 2026-09-14

Originally declared as `category.MESSAGING`. Play Store review (version 393, 2026-09-14) flagged
that the app fails messaging quality guidelines:
- No `MessagingTemplate` root screen showing historical conversations
- No in-app send/receive message flow per MESSAGING requirements

Changed to `category.OTHER` because:
1. This app is a terminal/AI session monitor, not a messaging app
2. `category.OTHER` exempts from messaging quality requirements while still allowing
   all templates we use (`ListTemplate`, `PaneTemplate`, `MessageTemplate`, etc.)
3. `CarAppExtender` notification Play/Reply action buttons continue to work with
   `category.OTHER` — they're bound to the service name, not the category
4. The only UX regression is loss of Gearhead's messaging-inbox auto-readout on
   phone-to-car Bluetooth connect; our explicit "Play" TTS button is the primary path

## Consequences
The public track passes Play policy review while the internal track provides a rich in-car experience for testing, with no risk of the unconstrained UI reaching the public Play Store.
