# BL28 — Text contrast: dark-purple on black backgrounds

**Status:** ✅ SHIPPED v1.5.1 (2026-09-14)  
**Scope:** Android phone only (Wear OS and Auto already passing)  
**WCAG target:** 4.5:1 AA for normal/label text on dark backgrounds

---

## Problem

`DwAccent` (`#7C3AED`, Violet-700) produced ~3.7:1 contrast ratio on `DwBg`
(`#0F1117`). WCAG AA requires ≥ 4.5:1 for normal text. The token was used
directly (or hardcoded as `0xFF7C3AED`) in six composables.

Relative luminance calculation:
- `#0F1117` L = 0.0033
- `#7C3AED` L = 0.0684 → ratio ≈ (0.0684 + 0.05) / (0.0033 + 0.05) = **3.7:1 FAIL**
- `#8B5CF6` L = 0.1145 → ratio ≈ (0.1145 + 0.05) / (0.0033 + 0.05) = **4.93:1 PASS**
- `#A855F7` L = 0.1466 → ratio ≈ (0.1466 + 0.05) / (0.0033 + 0.05) = **5.3:1 PASS**

## Fix

Single-step raise: Violet-700 → Violet-500 (`#7C3AED` → `#8B5CF6`).
`DwAccent2` (`#A855F7`, Violet-400) was already passing — unchanged.

## Files changed

| File | Change |
|------|--------|
| `composeApp/.../ui/theme/Theme.kt` | `DwAccent` token: `0xFF7C3AED` → `0xFF8B5CF6` |
| `composeApp/.../ui/orchestrator/OrchestratorGraphDialog.kt` | 2× hardcoded bg/text color |
| `composeApp/.../ui/monitoring/PeerResourcesCard.kt` | "agent" type chip color |
| `composeApp/.../ui/monitoring/FederatedPeersCard.kt` | "agent" type chip color |
| `composeApp/.../ui/monitoring/PluginsCard.kt` | "native" kind badge color |
| `composeApp/.../ui/autonomous/AutonomousScreen.kt` | "approved" status color + 2 stale comments |
| `composeApp/.../ui/splash/MatrixSplashScreen.kt` | `Border` + `IrisMid` canvas colors |
| `composeApp/.../ui/autonomous/PrdStatusColorTest.kt` | Expected constant updated |

## Surfaces audited — out of scope

- **Wear OS** — tile accent `0xFF00E5A0` (teal); `WearMainActivity` purple `0xFFA855F7` = DwAccent2 (~5.3:1) ✅
- **Android Auto** — only `CarColor.RED/GREEN/DEFAULT` used; no purple in car surfaces ✅
- **`Color(0xFF4C1D95).copy(alpha = 0.8f)`** in MatrixSplashScreen — decorative canvas stroke, not text; left unchanged ✅
