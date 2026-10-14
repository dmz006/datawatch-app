import SwiftUI
import UIKit
import MessageUI
import DatawatchShared

// Next-launch crash prompt (Send / View / Dismiss), report viewer, Send flow
// (mail composer when Mail is set up, otherwise the share sheet) and the
// Settings › About "Send bug report" sheet. Android: ui/about/CrashReportDialogs.kt.

/// Report text handed to a sheet.
struct ReportText: Identifiable {
    let id = UUID()
    let text: String
}

/// A pending report opened with View (kept so Send deletes exactly that one).
private struct ViewedReport: Identifiable {
    let report: PendingCrashReport
    let text: String
    var id: String { report.id }
}

// MARK: - Next-launch prompt

private struct CrashReportPrompt: ViewModifier {
    let enabled: Bool
    @ObservedObject private var reporter = CrashReporter.shared
    @State private var viewing: ViewedReport?
    @State private var sending: ReportText?

    private var current: PendingCrashReport? { enabled ? reporter.pending.first : nil }

    func body(content: Content) -> some View {
        content
            .alert(
                Text("datawatch closed unexpectedly. Send a report?"),
                isPresented: Binding(
                    get: { current != nil && viewing == nil && sending == nil },
                    set: { _ in }
                ),
                presenting: current
            ) { report in
                Button("Send") { send(report) }
                Button("View") { viewing = ViewedReport(report: report, text: reporter.text(for: report)) }
                Button("Dismiss", role: .cancel) { reporter.delete(report) }
            } message: { _ in
                Text("The report has the error, the app version, your device model and the recent app log. Server addresses, IP addresses and tokens are removed. Nothing is sent unless you tap Send.")
            }
            .sheet(item: $viewing) { item in
                ReportTextSheet(text: item.text) {
                    reporter.delete(item.report)
                }
            }
            .background(
                Color.clear.sheet(item: $sending) { item in
                    ReportSender(text: item.text) { sending = nil }
                }
            )
    }

    private func send(_ report: PendingCrashReport) {
        let text = reporter.text(for: report)
        reporter.delete(report)
        sending = ReportText(text: text)
    }
}

extension View {
    /// Offers a pending crash report once [enabled] (after the splash, while unlocked).
    func crashReportPrompt(enabled: Bool) -> some View {
        modifier(CrashReportPrompt(enabled: enabled))
    }
}

// MARK: - Viewer

/// Scrollable, selectable report text with Send and Close.
struct ReportTextSheet: View {
    let text: String
    /// Runs when the user sends from here (e.g. delete the pending report).
    var onSend: () -> Void = {}
    @Environment(\.dismiss) private var dismiss
    @State private var sending: ReportText?

    var body: some View {
        NavigationStack {
            ScrollView {
                Text(verbatim: text)
                    .font(DatawatchFonts.terminalSmall)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
            }
            .background(DatawatchColors.background)
            .navigationTitle(L("Report"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Send") {
                        onSend()
                        sending = ReportText(text: text)
                    }
                }
            }
            .sheet(item: $sending) { item in
                ReportSender(text: item.text) {
                    sending = nil
                    dismiss()
                }
            }
        }
    }
}

// MARK: - Send

/// Mail composer addressed to support when Mail is set up; otherwise the share
/// sheet (which still offers Mail, Messages, Files, ...). The text goes in the
/// body and as an attached datawatch-report.txt.
struct ReportSender: View {
    let text: String
    let onFinish: () -> Void

    var body: some View {
        if MFMailComposeViewController.canSendMail() {
            MailComposeView(text: text, onFinish: onFinish)
                .ignoresSafeArea()
        } else {
            ActivityView(items: activityItems, onFinish: onFinish)
                .ignoresSafeArea()
        }
    }

    private var activityItems: [Any] {
        var items: [Any] = [text]
        if let file = CrashReporter.temporaryFile(for: text) { items.append(file) }
        return items
    }
}

private struct ActivityView: UIViewControllerRepresentable {
    let items: [Any]
    let onFinish: () -> Void

    func makeUIViewController(context: Context) -> UIActivityViewController {
        let vc = UIActivityViewController(activityItems: items, applicationActivities: nil)
        vc.completionWithItemsHandler = { _, _, _, _ in onFinish() }
        return vc
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

private struct MailComposeView: UIViewControllerRepresentable {
    let text: String
    let onFinish: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onFinish: onFinish) }

    func makeUIViewController(context: Context) -> MFMailComposeViewController {
        let vc = MFMailComposeViewController()
        vc.mailComposeDelegate = context.coordinator
        vc.setToRecipients([IosCrashReports.shared.supportEmail])
        vc.setSubject(IosCrashReports.shared.subject)
        vc.setMessageBody(text, isHTML: false)
        if let data = text.data(using: .utf8) {
            vc.addAttachmentData(data, mimeType: "text/plain", fileName: "datawatch-report.txt")
        }
        return vc
    }

    func updateUIViewController(_ uiViewController: MFMailComposeViewController, context: Context) {}

    final class Coordinator: NSObject, MFMailComposeViewControllerDelegate {
        let onFinish: () -> Void

        init(onFinish: @escaping () -> Void) { self.onFinish = onFinish }

        func mailComposeController(
            _ controller: MFMailComposeViewController,
            didFinishWith result: MFMailComposeResult,
            error: Error?
        ) {
            onFinish()
        }
    }
}

// MARK: - Settings › About › Send bug report

/// No crash: the user's note plus the recent app log, shared the same way.
struct BugReportSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var note: String = ""
    @State private var preview: ReportText?
    @State private var sending: ReportText?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextEditor(text: $note)
                        .frame(minHeight: 140)
                        .font(DatawatchFonts.bodyMedium)
                } header: {
                    Text("What happened?")
                } footer: {
                    Text("Your note and the recent app log go into the report. Server addresses, IP addresses and tokens are removed. You choose where to send it.")
                }
                .listRowBackground(DatawatchColors.surface)
                Section {
                    Button("View") { preview = ReportText(text: build()) }
                        .foregroundStyle(DatawatchColors.primary)
                }
                .listRowBackground(DatawatchColors.surface)
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle(L("Send bug report"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Send") { sending = ReportText(text: build()) }
                }
            }
            .sheet(item: $preview) { item in
                ReportTextSheet(text: item.text)
            }
        }
        .background(
            Color.clear.sheet(item: $sending) { item in
                ReportSender(text: item.text) {
                    sending = nil
                    dismiss()
                }
            }
        )
    }

    private func build() -> String {
        IosCrashReports.shared.buildBugReport(note: String(note.prefix(4000)))
    }
}

#Preview("Report text") {
    ReportTextSheet(text: "datawatch app crash report\nKind: crash\nApp version: 1.0.0 (1)\n")
        .preferredColorScheme(.dark)
}

#Preview("Bug report") {
    BugReportSheet()
        .preferredColorScheme(.dark)
}
