# Crash and bug reports

The phone apps (Android and iOS) have a built-in crash and bug reporter. It runs
entirely on the device. There is no crash-reporting SDK and no network call. A
report only leaves the phone when you tap **Send** and pick where it goes.

## When you see it

- **After a crash.** The next time you open the app it asks *"datawatch closed
  unexpectedly. Send a report?"* with three choices:
  - **Send** opens your mail app addressed to support (or the share sheet, so you
    can pick any app). The report is in the message body and attached as
    `datawatch-report.txt`. You can edit or cancel the message before it goes.
  - **View** shows the full report text, exactly as it would be sent.
  - **Dismiss** deletes the report without sending it.

  Send and Dismiss both delete the report from the phone. On iOS the prompt waits
  until the app is unlocked if you use Face ID / Touch ID lock.
- **Any time.** Settings › About › Support › **Send bug report** builds a report
  without a crash: your note ("What happened?") plus the recent app log. You can
  view it before sending.

Support address: `datawatch.ai.bridge@gmail.com`. You can send the report anywhere
else instead, or not at all.

## What a report contains

```
datawatch app crash report
Kind: crash
App version: 1.28.17 (673)
Platform: Android
OS version: 14 (API 34)
Device: Google Pixel 8
Time: 2026-10-14T09:30:00.123Z
Thread: main

Stack trace:
java.lang.IllegalStateException: ...

Recent log (42 lines):
2026-10-14T09:29:58.001Z W/SessionWs: live stream error; reconnecting
...
```

| Field | Source |
|-------|--------|
| App version and version code | the app build |
| Platform, OS version, device model | Android `Build`; iOS `NSProcessInfo` and the hardware model id (e.g. `iPhone15,2`) |
| Time | when the crash happened (UTC) |
| Stack trace | the crash itself (see below) |
| Recent log | up to 300 recent app log lines, kept in memory only |
| What happened | your note (bug reports only) |

No account names, server profiles, session contents, terminal output, tokens or
device identifiers are added.

### What counts as a crash

| Platform | Captured | How |
|----------|----------|-----|
| Android | Uncaught Java/Kotlin exception | default uncaught-exception handler, report written before the app closes |
| Android 11+ | App Not Responding (ANR), with the system's ANR trace (first 64 KB) | `ActivityManager` exit-reason history, read on the next launch |
| Android 11+ | Native (C/C++) crash | same history; the description and signal only |
| iOS | Uncaught Kotlin exception in shared code | Kotlin/Native unhandled-exception hook |
| iOS | Uncaught Objective-C exception | `NSSetUncaughtExceptionHandler` |
| iOS | Fatal signal (SIGABRT, SIGSEGV, SIGBUS, SIGILL, SIGTRAP) | signal handler; a minimal report (header and signal name, no log) |
| iOS | Crash and hang diagnostics | Apple MetricKit, delivered on a later launch |

Each ANR or native crash is offered once. Android exits from before the reporter
was installed are not offered. At most 10 reports are kept; older ones are deleted.

## Redaction

Every report goes through the same redaction before you can view or send it.
It errs on the side of removing too much:

| Removed | Replaced with |
|---------|---------------|
| `Authorization:` / `Proxy-Authorization:` header values | `<redacted>` |
| `Bearer …` tokens | `Bearer <redacted>` |
| JSON Web Tokens | `<token>` |
| Secret-named values: `token=`, `key=`, `password:`, `secret=`, `api_key`, … | `<redacted>` |
| URLs: `https://host:port` (and any `user:password@`) | `<server>` (the path is kept) |
| Host names after "host" or "connect to", and `*.ts.net`, `.local`, `.lan`, `.internal`, `.home.arpa` names | `<server>` |
| IPv4 and IPv6 addresses | `<ip>` |
| Token-like strings: 32 or more letters and digits (hex or base64) | `<token>` |

Version numbers, timestamps, stack frames and UUIDs are kept.

## Privacy

- Nothing is collected or sent automatically, so the store privacy answers stay
  "no data collected". A report is only sent by your own mail or share app, after
  you choose Send.
- Pending reports live in app-private storage (Android `filesDir/crash/`, iOS
  Application Support `crash-reports/`) until you send or dismiss them.
- The recent-log buffer is in memory only and is cleared when the app closes.

## For developers

- Shared model: `shared/src/commonMain/kotlin/com/dmzs/datawatchclient/diagnostics/`
  (`DiagnosticsLog`, `CrashReport`, `BugReportBuilder`, `Redactor`), with tests in
  `shared/src/commonTest/.../diagnostics/`.
- Android: `composeApp/.../diagnostics/` (`CrashReporter`, `BugReportSharer`,
  `dwLog`) and `ui/about/CrashReportDialogs.kt`. Use `dwLog(tag, msg)` for
  failures worth seeing in a report; it writes to logcat and the in-memory log.
- iOS: `iosApp/iosApp/diagnostics/` (`CrashReporter.swift`, `CrashReportViews.swift`)
  and `shared/src/iosMain/.../diagnostics/IosCrashReports.kt`. Swift code can add
  log lines with `IosCrashReports.shared.logWarn(tag:message:)`.
