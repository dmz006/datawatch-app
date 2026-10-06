# ADR-0050 — iOS session drag-to-reorder keeps press-and-hold

## Status
Accepted (2026-10-06) — operator decision on backlog item BL401.

## Context

On Android and in the web UI, dragging the ⋮⋮ handle on a session card starts a
reorder immediately. The iOS sessions list uses SwiftUI `List.onMove`, which is
backed by the UIKit list drag: a drag only starts after a press-and-hold. The
⋮⋮ handle is shown on iOS too, but it cannot start the drag on its own.

Options:
- (a) Accept press-and-hold on iOS — platform-native behaviour, no new code.
- (b) Build a custom immediate drag (gesture + manual reordering) on iOS — more
  code, risk of fighting the list's own scroll/selection gestures, and a
  non-native feel.

## Decision

(a) Keep press-and-hold on iOS. The order is still persisted and shared the same
way (`cs_session_order` semantics); only the gesture that starts the drag
differs.

## Consequences

- The parity row "Drag handle ⋮⋮" in `docs/parity/sections/02-sessions-list.md`
  is aligned by decision (platform-idiomatic equivalent, AGENT.md › iOS Native
  Parity).
- No custom drag code to maintain on iOS.
