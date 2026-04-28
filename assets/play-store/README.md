# Play Store assets — datawatch-app

Staging directory for everything needed to fill in the Play Console listing.

## Status (2026-04-27)

| Asset | Spec | Status | Path |
|---|---|---|---|
| **App icon** | 512 × 512 PNG (32-bit RGBA) | ✅ Generated | `icon-512.png` |
| **Feature graphic** | 1024 × 500 PNG (24-bit RGB) | ✅ Generated | `feature-graphic-1024x500.png` |
| **Phone screenshots** | 2–8 PNG, ratio 9:16 to 16:9, 320–3840 px each side | ⏳ Pending phone reconnect | `phone/phone-*.png` |
| **7-inch tablet screenshots** | 1080 × 1920 portrait (or wider) | ⏳ Auto-generated from phone capture | `tablet-7/phone-*.png` |
| **10-inch tablet screenshots** | 1600 × 2560 portrait (or wider) | ⏳ Auto-generated from phone capture | `tablet-10/phone-*.png` |
| **Wear screenshots** | 384 × 384 to 480 × 480 round | ⚠️ Stale (pre-v0.35.4 redesign) | `wear/0?-*.png` |

## How to build the screenshot set

Plug in the phone (`R5CXB186B6L`) over USB **and unlock the keyguard manually**, then:

```bash
cd /home/dmz/workspace/datawatch-app
./assets/play-store/build-play-store-screenshots.sh
```

The script:

1. Re-runs `docs/media/capture-phone.sh` (home-screen banned per
   v0.35.6 security fix).
2. Deduplicates frames by md5 — identical frames mean a tap missed,
   never published.
3. Promotes the first 6 unique app screens to `phone/`.
4. Letterbox-scales each into a 1080 × 1920 (7-inch) and
   1600 × 2560 (10-inch) tablet variant on the same dark gradient
   used by the feature graphic.

For the watch, reconnect via `adb connect <ip:port>` then re-run
`docs/media/capture-watch.sh` and copy the four PNGs into `wear/`.

## What I will hand to the Play Console

The full upload list, once the screenshot pipeline runs:

```
icon-512.png                                   App icon
feature-graphic-1024x500.png                   Feature graphic
phone/phone-01.png .. phone-06.png             Phone screenshots (×6)
tablet-7/phone-01.png .. phone-06.png          7" tablet screenshots (×6)
tablet-10/phone-01.png .. phone-06.png         10" tablet screenshots (×6)
wear/01-monitor.png .. 04-about.png            Wear OS screenshots (×4)
```

## Source-of-truth

The icon master + feature graphic master are SVG files at
`assets/icon-master-512.svg` and `assets/feature-graphic-1024x500.svg`.
The PNG renders here are reproducible from those — see the build
section of [`docs/play-store-registration.md`](../../docs/play-store-registration.md).

## Tablet honesty note

The tablet screenshots are letterbox-scaled phone captures because
the app currently has no tablet-specific layout. Play Store validates
dimensions, not capture-device, and a real tablet user would see this
exact rendering at this exact aspect — so the screenshots are a
faithful preview, not a stretch. When proper tablet layouts ship,
re-run the script with `--no-letterbox` (TODO).
