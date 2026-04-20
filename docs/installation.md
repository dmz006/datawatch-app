# Installation

How to get the datawatch mobile app onto a phone, a Wear OS watch, or an
Android Auto head unit. v0.10.0 is distributed as APK artefacts on the
[GitHub release page](https://github.com/dmz006/datawatch-app/releases/tag/v0.10.0)
until the Play Store listing goes live. (v1.0.0 is reserved for the
release that reaches full PWA parity — see ADR-0043.)

## Before you start

You need a running [datawatch server](https://github.com/dmz006/datawatch)
v3.0.0 or newer. Make a note of:

1. Its base URL (e.g. `https://mymachine.taila1234.ts.net:8080`).
2. Its bearer token — find it under `~/.datawatch/config.yaml` → `api.token`
   or whatever you set in `DATAWATCH_API_TOKEN`.
3. Whether it uses a self-signed TLS cert (the Wizard option for local LAN
   deployments). If yes, you'll tick "Server uses a self-signed certificate"
   during add-server.

---

## Phone / tablet

### Prerequisites
- Android 10 (API 29) or newer.
- ~100 MB free storage.
- A network path to the datawatch server — most commonly one of:
  - Tailscale (recommended for LAN-restricted daemons)
  - Same Wi-Fi as the server
  - Public host + real TLS cert
  - Cloudflare Tunnel / Tailscale Funnel

### Installation

Download `datawatch-0.10.0.apk` from the v0.10.0 release.

**Option A — ADB (recommended)**

```bash
adb install -r datawatch-0.10.0.apk
```

**Option B — Sideload**

1. Move the APK to the device (USB, Drive, email).
2. Open the file manager, tap the APK.
3. When Android prompts "Install unknown apps?", grant the file manager
   (or browser) permission, then install.
4. Remove that permission afterwards.

Signature: SHA-256 in `datawatch-0.10.0-SHA256SUMS.txt`. Verify before installing:

```bash
sha256sum -c datawatch-0.10.0-SHA256SUMS.txt
```

### First launch

1. The splash screen plays once (about 3 s).
2. You land on the **Add server** screen.
3. Enter:
   - Display name (anything memorable — "laptop", "lab", etc.)
   - Base URL (`https://host:port` — trailing slash ignored)
   - Bearer token (or check "No bearer token" for an insecure test server)
   - "Self-signed certificate" if the server uses one
4. Tap **Add server**. The app runs a two-step probe (`/api/health` then
   `/api/sessions`) before saving. Errors surface inline.
5. You're dropped into the **Sessions** tab showing live session data.

### Enable push notifications

1. Android should prompt for POST_NOTIFICATIONS permission on first launch.
   If you declined, toggle it on in system Settings → Apps → datawatch →
   Notifications.
2. The app registers with the server automatically (`/api/devices/register`)
   on first successful connection. FCM tokens are preferred; absent a
   Firebase project the app falls back to ntfy and starts a foreground
   subscriber service you'll see in the notification drawer.

### Enable biometric unlock (optional)

Settings → Security → **Biometric unlock** toggle. Requires a Class-3
biometric (fingerprint or Class-3 face) to be enrolled system-wide.

### 3-finger-swipe server picker

On any screen, swipe **three fingers upward** to summon the bottom-sheet
server picker. Tap a server to switch; the Sessions tab immediately
reloads.

---

## Wear OS

### Prerequisites
- Wear OS 3 (API 30) or newer.
- Either:
  - Watch paired to the same phone you installed the phone app on, or
  - Developer options enabled on the watch + Wi-Fi ADB

### Installation via phone's adb bridge (paired watch, no Wi-Fi required)

1. On the phone, turn on Developer options → ADB debugging.
2. On the watch, turn on Developer options → **ADB debugging** AND
   **Debug over Bluetooth**.
3. Phone ↔ computer USB.
4. Enable Bluetooth debug bridging:

   ```bash
   adb forward tcp:4444 localabstract:/adb-hub
   adb connect 127.0.0.1:4444
   adb devices
   # You should now see a second device (your watch) in the list.
   ```

5. Install:

   ```bash
   adb -s 127.0.0.1:4444 install -r datawatch-wear-0.10.0.apk
   ```

### Installation via Wi-Fi ADB (watch with Wi-Fi)

1. On the watch, Settings → Developer options → **ADB over Wi-Fi** (record the IP).
2. From your computer:

   ```bash
   adb connect <watch-ip>:5555
   adb -s <watch-ip>:5555 install -r datawatch-wear-0.10.0.apk
   ```

### Add the Wear Tile

1. On the watch, swipe right to pass existing tiles until you reach **Add tile**.
2. Select **datawatch** from the list. The tile now shows running /
   waiting / total counts.

> The v0.10.0 Wear build shows zeros until the phone ↔ watch Data Layer pair
> flow ships in v0.11 — it needs the phone app to forward the live counts
> because the watch itself doesn't yet have a transport to datawatch. The
> Wear app and Tile are installed and ready; the pipe light turns on in v0.11.

---

## Android Auto

The Auto module is shipped inside the phone APK, so no separate install step
on the head unit is needed. After installing the phone app:

1. Connect your phone to an Auto-compatible head unit (USB cable or wireless).
2. On the head unit, you should see **datawatch** under "Communication"
   (because the app is declared as `androidx.car.app.category.MESSAGING`).
3. The Auto screen shows running / waiting / total counts. Per Play policy,
   deep interaction (reply composer, terminal) is intentionally blocked in
   the Auto Messaging category — use the phone / watch for that.

### Desktop Head Unit testing (DHU)

Developers can verify the Auto surface without a real car:

```bash
# Install the Desktop Head Unit from Android Studio → SDK Manager
~/Android/Sdk/extras/google/auto/desktop-head-unit
# With the phone connected and developer mode on, DHU mirrors the head-unit view.
```

---

## Troubleshooting

### "Server not reachable" on add-server
- Ping the base URL from a browser on the phone — if that fails, the
  issue is networking (Tailscale, Wi-Fi, VPN) and not the app.
- If you're using a self-signed cert, make sure the
  "Self-signed certificate" checkbox is ticked; without it the app rejects
  untrusted certs by design.

### "Token rejected by server"
- Your bearer token doesn't match `~/.datawatch/config.yaml → api.token`
  on the server, or the environment variable `DATAWATCH_API_TOKEN`. Fix and
  retry.

### Opening a session crashes (pre-v0.3.0 regression)
- v0.10.0 carries the SQLDelight 1 → 2 migration, so this should never
  happen. If it does, uninstall + reinstall to rebuild the encrypted DB
  fresh.

### Biometric toggle is greyed out
- Your device has no Class-3 biometric enrolled. Settings → Biometrics &
  Security → Fingerprint (or Face if your device's face is Class-3 —
  Samsung Galaxy S-series generally is) — enrol, then return to the app.

### Notifications not arriving
- Check Settings → Apps → datawatch → Notifications is enabled per
  channel (Input needed / Completed / Rate limited / Error).
- If the server didn't register the device (no FCM project configured
  server-side, no ntfy configured), there's no delivery channel yet — the
  app still works over REST + WebSocket while the session detail screen
  is open, but background wake won't fire. Configure `push.fcm_project`
  or `push.ntfy_server` server-side in `~/.datawatch/config.yaml`.

### APK install rejected as "corrupt"
- Check the SHA-256 against `datawatch-0.10.0-SHA256SUMS.txt`. A mismatch
  means a download problem, not a packaging problem.

---

## Uninstall

Standard Android uninstall. The app's encrypted DB and bearer tokens are
wiped by the uninstall (both live in app-private storage bound to the
app's install signature + user). Your datawatch server retains nothing
about the device unless you explicitly added a push registration — if you
did, visit `GET /api/devices` on the server and `DELETE` the record by
id. (The app calls `DELETE /api/devices/{id}` itself when you remove a
profile; a full uninstall doesn't get that chance.)
