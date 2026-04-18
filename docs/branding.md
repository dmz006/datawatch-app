# Branding

Matches the datawatch parent identity (ADR-0030): dark primary, purple accent
`#7c3aed` + `#a855f7`, the eye-of-surveillance motif. The mobile app extends this with a
"client agent" inflection so it reads as a *handset into* datawatch rather than the
watcher itself.

## Palette (from parent `style.css`)

| Token | Value | Usage |
|---|---|---|
| `--bg` | `#0f1117` | Root background |
| `--bg2` | `#1a1d27` | Cards, panels, bottom-sheet |
| `--bg3` | `#22263a` | Elevated surfaces, chips |
| `--surface` | `#1e2130` | Sheet surfaces |
| `--accent` | `#7c3aed` | Primary — CTAs, active states |
| `--accent2` | `#a855f7` | Secondary accent, hover |
| `--text` | `#e2e8f0` | Body |
| `--text2` | `#94a3b8` | Secondary text, labels |
| `--success` | `#10b981` | State: running / healthy |
| `--warning` | `#f59e0b` | State: rate-limited / attention |
| `--error` | `#ef4444` | State: error / destructive |
| `--waiting` | `#3b82f6` | State: waiting for input |
| `--border` | `#2d3148` | Dividers |

## Theme variants (ADR-0030)

All three ship from MVP:

1. **Dark** (default, matches parent).
2. **Light** — inverted palette with WCAG AA contrast:
   - `bg` → `#f8fafc`, `bg2` → `#e2e8f0`, `text` → `#1e293b`, accents darkened slightly.
3. **Material You dynamic** — uses Android 12+ `@android:color/system_accent1_*` values
   keyed off the user's wallpaper; semantic colors retained.

Theme is user-selectable in Settings → Appearance with a "Match system" default on Android
12+ and "Dark" default below.

## Typography

- **Body:** `system-ui, sans-serif` (parent uses system-ui for prose).
- **Monospace (session IDs, code, terminal):** `JetBrains Mono` vendored as a font resource.
- **Font size scale:** 10 / 11 / 12 / 13 / 14 / 15 / 16 / 18 / 20 / 24 — same scale as
  parent for consistent hierarchy.

## Iconography motifs

The parent datawatch icon is an **all-seeing eye** with crosshair pupil, hexagonal grid,
circuit traces, and watch tick marks — a literal "data watch." For the mobile client,
**Concept B is the chosen direction** (ADR-0037). Concepts A and C are retained as
alternates for future variants:

### Concept A — "Eye in hand"

A stylized hand silhouette in radial purple gradient cradling the datawatch eye.
Reads as: *you hold the watcher*. Preserves parent iconography 100%, adds the client
affordance.

```
ASCII sketch:

    ╭──╮
   ╱ ●● ╲      ← datawatch eye
   │ ◉◉ │
    ╲──╱
  ╭─────────╮
  │  ~~~~~  │  ← hand/grip
  ╰─────────╯
```

### Concept B — "Remote" ✅ chosen (ADR-0037)

A phone silhouette whose screen is a miniature datawatch eye with the crosshair turning
into an antenna arc above. Reads as: *client/remote into the system*. Stronger differentiation
from parent.

```
    ┌─────┐
    │  ) ))  ← signal arcs
    │  ●●  │ ← miniature eye
    │  ◉◉  │
    └─────┘
```

### Concept C — "Aperture + signal"

Parent's concentric rings open into a camera-aperture stop, with three radiating signal
arcs. Reads as: *scoped view into monitored systems*. Most abstract; most "professional app."

```
    ╭─╳─╮
   ╱  ◉  ╲
  │ ╭─╮ │
   ╲ ◉ ╱
    ╰─╯    ← aperture leaves
     )))    ← signal
```

Selected: Concept B. During Sprint 0 I produce 512/192/96/48 PNGs, adaptive icon foreground
+ background layers, monochrome variant (Android 13 themed icons), round variants for Wear
OS (384×384 round + square), and the Play Store feature graphic (1024×500) — all driven by
the Concept B silhouette + parent-palette gradients. The foreground signal arcs animate
subtly on app launch (Material 3 splash API) as a brand moment.

## Internal build differentiation

Per ADR-0031 the dev/internal build is a distinct installable. To prevent user confusion:

- **Dev icon:** same concept as chosen, plus a "DEV" badge chip in the lower-right (orange
  warning color), visible in launcher + recents.
- **Dev app name:** "datawatch (dev)" — matches ADR-0041 lowercase brand.
- **Dev app shortcut** on Auto is suppressed unless connected to DHU.

## Splash / launch

Material 3 splash API: scrim `#0f1117`, icon = chosen concept, tagline not shown on
splash (Material 3 recommends icon-only). First-open onboarding shows the full word mark.

## Word mark

The launcher icon label + Play Store name render as **`datawatch`** (lowercase, ADR-0041).
When a word mark appears in the app itself (splash, about screen, feature graphic), use
JetBrains Mono, letter-spacing 4px, accent color, matching parent datawatch's arc text
treatment but rendered straight instead of curved. Lowercase throughout — no "Client"
suffix, no uppercase stylization.

## Safe zones

- Adaptive icon: 108dp canvas, 72dp safe zone. Critical content within safe zone only.
- Feature graphic (1024×500): logo centered-left, color bleed right, "DATAWATCH CLIENT"
  word mark on the bleed, tagline below word mark.

## Play Store graphics checklist

- [ ] 512×512 PNG app icon (hi-res)
- [ ] 1024×500 PNG feature graphic
- [ ] 2–8 phone screenshots (1080×1920 portrait)
- [ ] 1+ tablet screenshots (7" and 10")
- [ ] 1+ Wear OS screenshots (384×384 round + square)
- [ ] 1+ Android Auto screenshots (head-unit aspect)
- [ ] Optional 30-second promo video

All mockable during Sprint 0; live screenshots captured in Sprint 5.
