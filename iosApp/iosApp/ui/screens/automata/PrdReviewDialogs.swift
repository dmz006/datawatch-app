import SwiftUI
import DatawatchShared

/// A pending review / lifecycle action on one PRD, raised from a list card's
/// lifecycle strip (D72a) or the detail screen (D74a).
/// `action`: "approve" | "reject" | "request_revision" | "cancel".
struct PrdReviewRequest: Identifiable, Equatable {
    let id = UUID()
    let prdId: String
    let title: String
    let action: String
    /// PRD status when raised — "planning" switches the cancel copy (PWA automataCancel).
    var status: String = ""
}

/// Approve-with-note (D74a; Android PrdDetailDialog approve dialog), Reject with
/// reason and Request revision with note (D72a; Android PrdRow inline dialogs),
/// plus Cancel confirm. `perform(prdId, action, body)` runs the request; "cancel"
/// arrives with a nil body and the caller maps it to the cancel endpoint.
struct PrdReviewDialogs: ViewModifier {
    @Binding var request: PrdReviewRequest?
    let perform: (String, String, [String: String]?) -> Void
    @State private var text = ""

    func body(content: Content) -> some View {
        content
            .alert("Approve", isPresented: shown("approve")) {
                TextField("Note (optional)", text: $text)
                Button("Approve") { fire(key: "note", alwaysActor: true) }
                Button("Cancel", role: .cancel) { clear() }
            } message: {
                Text(request?.title ?? "")
            }
            .alert("Reject automaton", isPresented: shown("reject")) {
                TextField("Reason", text: $text)
                Button("Reject", role: .destructive) { fire(key: "reason", alwaysActor: false) }
                Button("Cancel", role: .cancel) { clear() }
            } message: {
                Text("The automaton moves to rejected. The reason is recorded with it.")
            }
            .alert("Request revision", isPresented: shown("request_revision")) {
                TextField("What should change?", text: $text)
                Button("Send") { fire(key: "note", alwaysActor: false) }
                Button("Cancel", role: .cancel) { clear() }
            }
            .alert("Cancel automaton?", isPresented: shown("cancel")) {
                Button("Cancel automaton", role: .destructive) {
                    if let r = request { perform(r.prdId, "cancel", nil) }
                    clear()
                }
                Button("Keep running", role: .cancel) { clear() }
            } message: {
                Text(PrdReviewDialogs.cancelMessage(status: request?.status ?? ""))
            }
    }

    /// PWA `automataCancel`: planning gets the abort warning; otherwise the
    /// standard cancel copy.
    static func cancelMessage(status: String) -> String {
        if status.lowercased() == "planning" {
            return L("⚠️ Planning is running on the server.\n\nCancelling will abort the entire automaton — the plan being generated will be discarded. The server-side planning job may continue briefly before stopping.\n\nCancel the automaton?")
        }
        return L("Running tasks are stopped. The automaton and its history are kept.")
    }

    private func shown(_ action: String) -> Binding<Bool> {
        Binding(
            get: { request?.action == action },
            set: { if !$0 && request?.action == action { request = nil } }
        )
    }

    /// Approve sends `{"actor":"operator"}` plus `note` when one was typed (Android
    /// `approve(prdId, note)`); reject / revise always send their text field.
    private func fire(key: String, alwaysActor: Bool) {
        guard let r = request else { return }
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        var body: [String: String] = [:]
        if alwaysActor { body["actor"] = "operator" }
        if !value.isEmpty || !alwaysActor { body[key] = value }
        perform(r.prdId, r.action, body)
        clear()
    }

    private func clear() {
        text = ""
        request = nil
    }
}

extension View {
    func prdReviewDialogs(
        _ request: Binding<PrdReviewRequest?>,
        perform: @escaping (String, String, [String: String]?) -> Void
    ) -> some View {
        modifier(PrdReviewDialogs(request: request, perform: perform))
    }
}
