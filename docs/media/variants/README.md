# Icon variants — review set

Four Concept B iterations. All share palette + parent-datawatch motif; they vary in the
phone/eye balance and in how much surveillance-motif detail survives at launcher size.

Render each one at launcher scale in your head (72 px adaptive-icon safe zone on a 108 px
canvas, cropped to a circle on Material You). The one that reads fastest at thumb-size
wins.

| File | What's different | When it wins |
|------|------------------|--------------|
| `icon-B1-current.svg` | Baseline committed foreground: balanced phone + mini eye + 3 arcs | If current reads well and nothing needs changing |
| `icon-B2-larger-eye.svg` | Eye inflated to ~60% of screen; phone narrowed; arcs trimmed to 2 | When the eye is the brand anchor — bigger eye = more recognizably "datawatch" |
| `icon-B3-minimal-phone.svg` | Handset silhouette as a clean outline only (no speaker, no home indicator), heavier accent eye, 2 arcs | Cleaner at small sizes; less retro / less literal |
| `icon-B4-eye-on-screen` | Drops the phone outline entirely; mini eye sits inside a stylized screen that IS the icon; arcs emerge from the screen frame | Most abstract; most "app-like"; loses the handset metaphor |

## How to view

- GitHub renders SVG inline — click any file on
  https://github.com/dmz006/datawatch-app/tree/main/assets/variants to preview.
- Or open locally in any browser: `file:///.../assets/variants/icon-B2-larger-eye.svg`.

## Next step

Reply with the winner (B1/B2/B3/B4) or describe what to try next. I'll promote the
winner into `composeApp/src/androidMain/res/drawable/ic_launcher_foreground.xml`, the
dev-variant override, and the monochrome / feature-graphic downstream renders.
