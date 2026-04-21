# Wear OS Surfaces

*Last updated 2026-04-22 for v0.33.0.*

Extension-only. Token inherited silently from the phone over Wearable
Data Layer API (ADR-0029). MVP shipped notification + complication +
rich app (ADR-0028) at v0.5.0+ and has been stable through v0.33.
Tile (W2) remains deferred pending usage telemetry.

**v0.33 bundling status:** Wear AAB has been embedded in the phone
AAB since v0.5.0 — Play Console handles delivery to paired watches
automatically. (For comparison, the Auto surface was only bundled
starting v0.33.0; Wear has not had that issue.)

## Module

`wear/` Gradle module. Depends on `shared` KMP module (commonMain + androidMain where
needed). Separate `applicationId` namespace: `com.dmzs.datawatchclient.wear` paired with
both phone flavors (public + dev) via the same package sharing logic.

## Surfaces

### W1 — Ongoing notification with MessagingStyle (required)

- Phone app posts notifications via `NotificationManager` using `MessagingStyle` so Wear
  mirrors them natively with reply actions.
- Actions on the notification: `Approve`, `Deny`, `Reply` (voice), `Mute 10m`.
- Reply action uses `RemoteInput.setAllowFreeFormInput(true)` + `setAllowVoice(true)` so
  the watch offers dictation automatically.

```kotlin
val remoteInput = RemoteInput.Builder(KEY_REPLY)
    .setLabel("Reply")
    .setAllowFreeFormInput(true)
    .setChoices(arrayOf("Continue", "Retry", "Stop"))  // quick replies
    .build()

val replyAction = NotificationCompat.Action.Builder(
    R.drawable.ic_reply_wear, "Reply", replyPendingIntent
).addRemoteInput(remoteInput)
 .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
 .setShowsUserInterface(false)
 .build()
```

### W3 — Watchface complication

- `ComplicationDataSourceService` publishes:
  - **Ranged-value complication**: "3 sessions, 1 needs input" with progress ring showing
    waiting-count over total.
  - **Short-text complication**: "3/1" (total/waiting).
  - **Long-text complication**: "3 running · 1 waiting · last 2m ago".
- Supported complication types: `RANGED_VALUE`, `SHORT_TEXT`, `LONG_TEXT`, `ICON`.
- Data refresh: push-driven via Wear Data Layer messages from the phone; fallback periodic
  refresh every 15 min.
- Tap → opens Wear rich app (W4) to sessions list.

### W4 — Rich Wear app

Screens:
1. **Session list** — scrollable Wear `ScalingLazyColumn`, each row = session id + state
   icon + last-activity. Top chip shows active server; swipe left/right to switch.
2. **Session detail** — last 5 messages, pending prompt highlighted, action chips (Reply,
   Approve, Deny, Mute).
3. **Voice compose** — tap FAB → Wear RemoteInput dictation → preview → send.
4. **Stats** — single-screen summary (CPU, mem, running count). Read-only.
5. **Server picker** — list of profiles, tap to switch active.

Material 3 for Wear components. Dark theme only (no Material You on Wear as of 2026).

### W2 — Tile (post-MVP)

Deferred. Wear Tiles could show session counts at a glance without opening the app. Add
post-MVP only if usage metrics indicate the complication + notifications are insufficient.

## Data Layer API

```kotlin
// phone: push session state diff to watch
val dataClient = Wearable.getDataClient(context)
val request = PutDataMapRequest.create(PATH_SESSION_STATE).apply {
    dataMap.putString("server", serverId)
    dataMap.putInt("running", running)
    dataMap.putInt("waiting", waiting)
    dataMap.putLong("ts", nowMs)
}.asPutDataRequest().setUrgent()
dataClient.putDataItem(request)

// wear: listen for diffs
class StateListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) { … }
}
```

Token is **not** sent over Data Layer. When the watch needs to call the server, it sends a
MessageApi request to the phone, which proxies the REST call. This keeps the token in a
single place (phone Keystore).

```
Watch  --MessageApi("session_reply", payload)-->  Phone
Phone  --HTTPS bearer auth-->  datawatch server
Phone  <--HTTPS response--  datawatch server
Watch  <--MessageApi response--  Phone
```

## Standalone-mode behavior

If the watch is connected to Wi-Fi but not paired (rare on Wear OS 4+), the app refuses to
function and shows "Open datawatch on your phone to sync." No token leakage path.

## Testing

- Unit: ViewModels shared with phone.
- Wear emulator: screenshots of each surface; complication behavior per watchface.
- Live: paired Galaxy Watch + Pixel Watch tested before marking `Validated=Yes`.

## Packaging

- Wear module produces its own AAB included in the phone app's AAB bundle (embedded Wear
  app distribution).
- Play Console handles delivery to paired watches automatically.
