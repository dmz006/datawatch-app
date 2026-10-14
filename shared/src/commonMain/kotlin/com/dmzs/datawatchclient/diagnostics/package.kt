/**
 * On-device crash and bug reporting.
 *
 * - [DiagnosticsLog]: a small in-memory ring buffer of recent app log lines.
 * - [CrashReport] / [BugReportBuilder]: turn a crash (or a user's "what happened"
 *   note) plus the recent log into a plain-text report.
 * - [Redactor]: strips bearer tokens, `Authorization` headers, server URLs and
 *   hosts, IP addresses and token-like strings from every report.
 *
 * **Design invariants:** nothing here sends data anywhere. Reports are written to
 * app-private storage and only leave the device when the user picks Send, which
 * hands the text to the platform share sheet or mail composer. No third-party SDK.
 * See `docs/crash-reports.md`.
 */
package com.dmzs.datawatchclient.diagnostics
