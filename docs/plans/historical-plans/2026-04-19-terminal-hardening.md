# Terminal hardening (B1)

- **Date started:** 2026-04-19
- **Version at plan time:** v0.10.1
- **Target ship version:** v0.10.2 (patch)
- **Bug:** B1 — terminal freezes on session open; no scroll, no font adjust, no ANSI colour render
- **Scope:** `composeApp/src/androidMain/kotlin/com/dmzs/datawatchclient/ui/sessions/TerminalView.kt`, `composeApp/src/androidMain/assets/xterm/host.html`, optional `SessionDetailScreen.kt` for wiring.

## Root-cause hypotheses (in order of probability)

1. **State leak across session switches.** `TerminalView` holds `ready`, `lastWrittenIndex`, `webViewRef` in `remember {}` without a `sessionId` key. When Compose reuses the `AndroidView` across a navigation between sessions A → B:
   - `lastWrittenIndex` carries A's value (e.g. 42). B's ViewModel ships a fresh `state.events` list starting at 0. The LaunchedEffect is keyed on `(events.size, ready)` — `events.size` might be < `lastWrittenIndex` or the initial-flush branch (`lastWrittenIndex == 0 && events.isNotEmpty()`) evaluates false because `lastWrittenIndex` is stale. Net: nothing is ever written into xterm for session B, user sees the host.html banner and then silence.
   - The WebView's DOM still shows session A's output (no `dwClear()` call on switch).
2. **FitAddon captured pre-layout dimensions.** `term.open()` runs before the AndroidView container has a stable size. host.html retries `safeFit()` at rAF + 50 ms + 200 ms + 600 ms, but on a slow device the WebView may still be 0×0 at 600 ms, so the last fit bails (`width < 40 || height < 40`). xterm then renders at the 80×24 fallback, but the actual viewport might clamp to 1–2 visible rows, looking frozen.
3. **`onReady` never fires.** If the `DwBridge.onReady` injected-object call is blocked by a WebView policy or the JS never reaches line 167 of host.html (e.g., `try {} catch {}` swallows an error earlier), `ready` stays `false` and the LaunchedEffect's guard at line 103 returns early forever.

## Phases

### Phase 1 — reset terminal state on session switch (Done)

- Accept `sessionId: String` as a parameter on `TerminalView`.
- In a `LaunchedEffect(sessionId)`: reset `lastWrittenIndex = 0`, call `webView.evaluateJavascript("window.dwClear && window.dwClear();", null)`. Do not reset `ready` — the bridge only fires `onReady` once per WebView, and the WebView is kept.
- Pass `sessionId` from `SessionDetailScreen` into `TerminalView`.

**Acceptance:** opening session A, navigating back, opening session B writes session B's backlog into the xterm surface within 500 ms (verifiable from `DwTerm` logcat: `initial flush: N chars`).

### Phase 2 — make the fit race observable and deterministic (Partially done — retries extended; new logging deferred)

- host.html: log `container.getBoundingClientRect()` + `term.cols/rows` on every `safeFit` attempt so `adb logcat DwTerm:V *:S` tells us exactly why a fit skipped.
- Add one more retry at +1200 ms and +2500 ms to cover slow devices.
- If the three-retry ladder still sees `skip fit: container 0x0` at the last step, post a `dwReady` anyway so the LaunchedEffect can flush into an 80×24 fallback grid rather than hanging on `!ready`.

**Acceptance:** on B1 repro, logcat shows at least one `fit ok: …` line with width ≥ 40 and height ≥ 40 within 3 seconds of screen open.

### Phase 3 — confirm `onReady` path (Done)

- Add a fallback: if `DwBridge.onReady()` hasn't fired within 2 s, post it from `onPageFinished` (Kotlin side) so `ready` flips even if the JS-side call was swallowed. Idempotent on the Kotlin side.

**Acceptance:** `DwTerm onReady` appears in logcat within 2 s of page finish on every session open.

### Phase 4 — live validation (Planned)

- User reproduces on session `787e*` with `adb logcat DwTerm:V *:S`. Paste the log. Decide whether Phase 1 alone fixed it, or whether the FitAddon race (Phase 2/3) is also needed.
- Once green: update `docs/testing-tracker.md` B1 row. Close B1 in `docs/plans/README.md`.

## Out of scope for this patch

- Adjustable font size / pinch-to-zoom — tracked as BL13.
- xterm search + copy affordances — v0.11 per `docs/parity-plan.md`.
- Input echo from terminal back to the WS reply lane — still Sprint-3 era no-op; not a regression.
