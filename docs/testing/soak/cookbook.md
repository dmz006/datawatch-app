# datawatch-app — Soak Test Cookbook (Live Status)

**Last updated**: 2026-05-16
**Test host**: johnnyjohnny (32G GPU, Ollama `qwen3:1.7b`)
**Test environment**: Secondary instance (https://10.0.2.2:18443, ports 18080/18443) + emulator dw_test_phone
**Story namespace**: SS-001–SS-020 (separate from TS-XXX)
**IMPORTANT**: All soak tests run against the secondary test instance. Production johnnyjohnny (8080/8443) receives only result summary POSTs via /api/test/message.

After each story run: update the Status column with pass/fail and heap delta. Preserve FAIL evidence in `evidence/`.

---

## Sprint Summary

| Story | Title | Duration | Status | Heap Delta | Notes |
|-------|-------|----------|--------|------------|-------|
| SS-001 | 2-hour continuous poll refresh | 2 h | 📋 Pending | — | — |
| SS-002 | 100x on-resume refresh cycle | ~50 min | 📋 Pending | — | — |
| SS-003 | 50x screen lock/unlock cycle | ~25 min | 📋 Pending | — | — |
| SS-004 | 30x tab switch refresh cycle | ~15 min | 📋 Pending | — | — |
| SS-005 | 4-hour keep-alive session | 4 h | 📋 Pending | — | — |
| SS-006 | 200x alert dismiss cycle | ~30 min | 📋 Pending | — | — |
| SS-007 | 50x server switch cycle | ~25 min | 📋 Pending | — | — |
| SS-008 | 100x new session + kill cycle | ~50 min | 📋 Pending | — | — |
| SS-009 | 3-hour autonomous PRD poll | 3 h | 📋 Pending | — | — |
| SS-010 | WebSocket reconnect storm (20x) | ~40 min | 📋 Pending | — | — |
| SS-011 | 50x multi-server tab switch | ~25 min | 📋 Pending | — | — |
| SS-012 | 100x filter chip cycle | ~20 min | 📋 Pending | — | — |
| SS-013 | 75x sort order cycle | ~15 min | 📋 Pending | — | — |
| SS-014 | 150x session detail open/close | ~40 min | 📋 Pending | — | — |
| SS-015 | 50x bulk select + deselect | ~20 min | 📋 Pending | — | — |
| SS-016 | 2-hour alert badge refresh | 2 h | 📋 Pending | — | — |
| SS-017 | 60x history toggle cycle | ~15 min | 📋 Pending | — | — |
| SS-018 | 40x terminal scroll cycle | ~30 min | 📋 Pending | — | — |
| SS-019 | 80x notification dismiss cycle | ~20 min | 📋 Pending | — | — |
| SS-020 | 2-hour full session lifecycle loop | 2 h | 📋 Pending | — | — |
| **TOTALS** | | **~27 h** | **0/20 pass** | — | — |

---

## Story Status Detail

### SS-001 — 2-Hour Continuous Poll Refresh

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| Heap start | — |
| Heap end | — |
| Heap delta | — |
| WS reconnect Δ | — |
| ANR count | — |
| Evidence file | — |
| Notes | — |

---

### SS-002 — 100x On-Resume Refresh Cycle

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| Heap start | — |
| Heap end | — |
| Heap delta | — |
| Crash count | — |
| Evidence file | — |
| Notes | — |

---

### SS-003 — 50x Screen Lock/Unlock Cycle

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| Heap start | — |
| Heap end | — |
| Heap delta | — |
| WS reconnect Δ | — |
| Evidence file | — |
| Notes | — |

---

### SS-004 — 30x Tab Switch Refresh Cycle

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| Session count drift | — |
| Alert badge drift | — |
| Heap delta | — |
| Evidence file | — |
| Notes | — |

---

### SS-005 — 4-Hour Keep-Alive Session

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| Heap at T+1h | — |
| Heap at T+2h | — |
| Heap at T+3h | — |
| Heap at T+4h | — |
| Disconnect events | — |
| Evidence file | — |
| Notes | — |

---

### SS-006 — 200x Alert Dismiss Cycle

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| Badges cleared (of 200) | — |
| Heap delta | — |
| Residual unread count | — |
| Evidence file | — |
| Notes | — |

---

### SS-007 — 50x Server Switch Cycle

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| OkHttp connection Δ | — |
| Heap delta | — |
| Evidence file | — |
| Notes | — |

---

### SS-008 — 100x New Session + Kill Cycle

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| List count stable (of 100) | — |
| Heap delta | — |
| Evidence file | — |
| Notes | — |

---

### SS-009 — 3-Hour Autonomous PRD Poll

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| Heap at T+1h | — |
| Heap at T+2h | — |
| Heap at T+3h | — |
| PRD state at T+3h | — |
| Evidence file | — |
| Notes | — |

---

### SS-010 — WebSocket Reconnect Storm (20x)

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| Reconnects succeeded (of 20) | — |
| Max reconnect latency | — |
| Heap delta | — |
| Evidence file | — |
| Notes | — |

---

### SS-011 — 50x Multi-Server Tab Switch

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| State bleed events | — |
| Heap delta | — |
| Evidence file | — |
| Notes | — |

---

### SS-012 — 100x Filter Chip Cycle

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| Count stable (of 100) | — |
| Heap delta | — |
| Evidence file | — |
| Notes | — |

---

### SS-013 — 75x Sort Order Cycle

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| Sort correct (of 75) | — |
| Heap delta | — |
| Evidence file | — |
| Notes | — |

---

### SS-014 — 150x Session Detail Open/Close

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| Heap at 0 iter | — |
| Heap at 50 iter | — |
| Heap at 100 iter | — |
| Heap at 150 iter | — |
| Evidence file | — |
| Notes | — |

---

### SS-015 — 50x Bulk Select + Deselect

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| Clean exits (of 50) | — |
| Heap delta | — |
| Evidence file | — |
| Notes | — |

---

### SS-016 — 2-Hour Alert Badge Refresh

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| Badge accurate (of 8 samples) | — |
| Heap delta | — |
| Residual unread at end | — |
| Evidence file | — |
| Notes | — |

---

### SS-017 — 60x History Toggle Cycle

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| Count stable (of 60) | — |
| Heap delta | — |
| Evidence file | — |
| Notes | — |

---

### SS-018 — 40x Terminal Scroll Cycle

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| Heap at 0 iter | — |
| Heap at 20 iter | — |
| Heap at 40 iter | — |
| Evidence file | — |
| Notes | — |

---

### SS-019 — 80x Notification Dismiss Cycle

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| Notifications appeared (of 80) | — |
| Max notification latency | — |
| Evidence file | — |
| Notes | — |

---

### SS-020 — 2-Hour Full Session Lifecycle Loop

| Field | Value |
|-------|-------|
| Status | 📋 Pending |
| Run date | — |
| Duration | — |
| Cycles completed | — |
| Heap at T+1h | — |
| Heap at T+2h | — |
| WS reconnect Δ | — |
| Running count at end | — |
| Evidence file | — |
| Notes | — |

---

## Soak Pass/Fail Legend

| Symbol | Meaning |
|--------|---------|
| 📋 Pending | Not yet run |
| ✅ Pass | All pass criteria met |
| ⚠️ Warning | Heap delta 20–50 MB; no crash; investigate before ship |
| ❌ Fail | Any pass criterion violated |
| 🔁 Re-run | Previously failed; fix applied; re-running |

---

*Soak cookbook created 2026-05-16. Update after each story run.*
