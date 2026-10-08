import SwiftUI
import DatawatchShared

// Session-detail controls split out of SessionDetailView to keep its body
// small enough for the Swift type checker.

// MARK: - Info-bar actions (PWA btn-stop / btn-restart / btn-delete)

/// `■ Stop` while active; `↻ Restart` + `🗑 Delete` once done (D44a wording;
/// PWA info bar, left of the state pill).
struct SessionActionButtons: View {
    let isDone: Bool
    let busy: Bool
    var onStop: () -> Void
    var onRestart: () -> Void
    var onDelete: () -> Void

    var body: some View {
        HStack(spacing: 6) {
            if isDone {
                pill("↻ " + L("Restart"), tint: DatawatchColors.primary, action: onRestart)
                pill("🗑 " + L("Delete"), tint: DatawatchColors.error, action: onDelete)
            } else {
                pill("■ " + L("Stop"), tint: DatawatchColors.error, action: onStop)
            }
        }
        .disabled(busy)
    }

    private func pill(_ text: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text)
                .font(DatawatchFonts.badge)
                .foregroundStyle(tint)
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .overlay(Capsule().stroke(tint.opacity(0.6), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Running pill pulse (D18a; PWA dw-running-pulse 700 ms .55 ↔ 1.0)

struct RunningPulse: ViewModifier {
    let active: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var dim = false

    func body(content: Content) -> some View {
        content
            .opacity(active && !reduceMotion && dim ? 0.55 : 1.0)
            .onAppear { update() }
            .onChange(of: active) { _ in update() }
    }

    private func update() {
        if active && !reduceMotion {
            withAnimation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true)) { dim = true }
        } else {
            withAnimation(.default) { dim = false }
        }
    }
}

// MARK: - Saved commands + arrows (PWA savedCmdsQuick, D21b)

/// PWA `loadSavedCmdsQuick`: a literal "Commands…" dropdown (System · Saved ·
/// Custom…) with an inline custom-command row, plus the ␛ ↑ ↓ ← → ⏎ group on
/// the right where the arrows repeat while held (PWA `startArrowRepeat`).
struct SavedCommandsRow: View {
    let profile: ServerProfile
    let session: DwSession
    var onSent: (String) -> Void = { _ in }

    @State private var saved: [IosSavedCommand] = []
    @State private var customOpen = false
    @State private var custom = ""

    /// PWA system set, in order: label → value.
    static let system: [(label: String, value: String)] = [
        ("approve", "yes"), ("reject", "no"), ("enter", "\n"), ("continue", "continue"),
        ("skip", "skip"), ("abort", "\u{03}"), ("ESC", "__esc__"),
        ("tmux prefix (Ctrl-b)", "__ctrlb__"), ("quit", "/exit"),
    ]

    var body: some View {
        VStack(spacing: 4) {
            HStack(spacing: 6) {
                commandsMenu
                Spacer(minLength: 4)
                arrowGroup
            }
            if customOpen { customRow }
        }
        .padding(.horizontal, 12)
        .padding(.top, 6)
        .onAppear(perform: load)
    }

    private var userSaved: [IosSavedCommand] {
        // Web UI hides server-seeded commands (they duplicate the System set).
        saved.filter { !$0.seeded }
    }

    private var commandsMenu: some View {
        Menu {
            Section(L("System")) {
                ForEach(Self.system, id: \.value) { c in
                    Button(c.label) { send(c.value) }
                }
            }
            if !userSaved.isEmpty {
                Section(L("Saved")) {
                    ForEach(userSaved, id: \.name) { c in
                        Button(c.name.isEmpty ? c.command : c.name) { send(c.command) }
                    }
                }
            }
            // Web UI "Guardrails" group: run one built-in guardrail on this session.
            Section(L("Guardrails")) {
                ForEach(IosGuardrails.shared.builtins, id: \.self) { g in
                    Button("▶ " + g) { runGuardrail(g) }
                }
            }
            Section {
                Button(L("Custom…")) { customOpen = true }
            }
        } label: {
            HStack(spacing: 4) {
                Text("Commands…")
                Image(systemName: "chevron.down").font(.system(size: 9, weight: .bold))
            }
            .font(DatawatchFonts.labelSmall)
            .foregroundStyle(DatawatchColors.onSurface)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 6))
        }
        .accessibilityLabel("Saved commands")
    }

    private var customRow: some View {
        HStack(spacing: 6) {
            TextField("Type command…", text: $custom)
                .font(DatawatchFonts.bodyMedium)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.send)
                .onSubmit(sendCustom)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 6))
            Button(action: sendCustom) { Text("➤") }
                .buttonStyle(.borderless)
                .disabled(custom.trimmingCharacters(in: .whitespaces).isEmpty)
                .accessibilityLabel("Send")
            Button { custom = ""; customOpen = false } label: { Text("✕") }
                .buttonStyle(.borderless)
                .accessibilityLabel("Cancel")
        }
    }

    private var arrowGroup: some View {
        HStack(spacing: 4) {
            KeyGlyphButton(glyph: "␛", label: "Escape", repeats: false) { key("Escape") }
            KeyGlyphButton(glyph: "↑", label: "Arrow up", repeats: true) { key("Up") }
            KeyGlyphButton(glyph: "↓", label: "Arrow down", repeats: true) { key("Down") }
            KeyGlyphButton(glyph: "←", label: "Arrow left", repeats: true) { key("Left") }
            KeyGlyphButton(glyph: "→", label: "Arrow right", repeats: true) { key("Right") }
            KeyGlyphButton(glyph: "⏎", label: "Enter", repeats: false) { key("Enter") }
        }
    }

    private func runGuardrail(_ name: String) {
        AlertDock.shared.post(L("Running guardrail:") + " " + name)
        IosGuardrails.shared.run(profile: profile, sessionId: session.fullId, guardrail: name, onSuccess: { r in
            Task { @MainActor in
                let text = name + ": " + r.outcome + (r.summary.isEmpty ? "" : " — " + String(r.summary.prefix(60)))
                AlertDock.shared.post(text, level: r.outcome == "pass" ? .success : .error)
            }
        }, onError: { err in
            Task { @MainActor in AlertDock.shared.post(L("Guardrail error:") + " " + err, level: .error) }
        })
    }

    private func load() {
        IosQuickCommands.shared.loadSaved(profile: profile) { list in
            DispatchQueue.main.async { saved = list }
        }
    }

    private func key(_ name: String) {
        _ = IosSessionOps.shared.sendKey(session: session, key: name)
    }

    private func sendCustom() {
        let text = custom.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        send(text)
        custom = ""
        customOpen = false
    }

    /// PWA `sendSavedCmd` over the session's open /ws.
    private func send(_ value: String) {
        let ok: Bool
        switch value {
        case "\n", "": ok = IosSessionOps.shared.sendKey(session: session, key: "Enter")
        case "\u{03}": ok = IosSessionOps.shared.sendKey(session: session, key: "C-c")
        case "__esc__": ok = IosSessionOps.shared.sendKey(session: session, key: "Escape")
        case "__ctrlb__": ok = IosSessionOps.shared.sendKey(session: session, key: "C-b")
        default: ok = IosSessionOps.shared.sendText(session: session, text: value + "\r")
        }
        if ok {
            onSent(value)
        } else {
            AlertDock.shared.post(L("Not connected to the session — try again in a moment."), level: .error)
        }
    }
}

/// A key glyph that fires on press and, when `repeats`, keeps firing while held
/// (PWA `startArrowRepeat`: 250 ms delay, then every 80 ms).
struct KeyGlyphButton: View {
    let glyph: String
    let label: String
    let repeats: Bool
    var action: () -> Void

    @State private var pressed = false
    @State private var timer: Timer? = nil

    var body: some View {
        Text(glyph)
            .font(.system(.callout, design: .monospaced).weight(.semibold))
            .foregroundStyle(DatawatchColors.onSurface)
            .frame(minWidth: 32, minHeight: 30)
            .background(pressed ? DatawatchColors.border : DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 6))
            .contentShape(Rectangle())
            .gesture(press)
            .accessibilityElement()
            .accessibilityLabel(L(label))
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { action() }
            .onDisappear(perform: stop)
    }

    private var press: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                guard !pressed else { return }
                pressed = true
                action()
                guard repeats else { return }
                timer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: false) { _ in
                    DispatchQueue.main.async { startRepeat() }
                }
            }
            .onEnded { _ in stop() }
    }

    private func startRepeat() {
        guard pressed else { return }
        timer = Timer.scheduledTimer(withTimeInterval: 0.08, repeats: true) { _ in
            DispatchQueue.main.async { action() }
        }
    }

    private func stop() {
        pressed = false
        timer?.invalidate()
        timer = nil
    }
}

// MARK: - Pending schedules strip (PWA loadSessionSchedules)

struct PendingSchedulesStrip: View {
    let profile: ServerProfile
    let session: DwSession
    /// Bump to reload (e.g. after the schedule sheet closes).
    let reloadToken: Int

    @State private var items: [IosPendingSchedule] = []

    var body: some View {
        Group {
            if !items.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("🕑 " + String(format: L("Scheduled (%d)"), items.count))
                        .font(DatawatchFonts.labelSmall.weight(.semibold))
                        .foregroundStyle(DatawatchColors.warning)
                    ForEach(items, id: \.id) { item in row(item) }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(DatawatchColors.surface)
                .overlay(Divider().background(DatawatchColors.border), alignment: .bottom)
            }
        }
        .task(id: reloadToken) { load() }
    }

    private func row(_ item: IosPendingSchedule) -> some View {
        HStack(spacing: 8) {
            Text(whenText(item))
                .font(DatawatchFonts.terminalSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            Text(item.command)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurface)
                .lineLimit(1)
            Spacer(minLength: 4)
            Button { cancel(item) } label: {
                Text("✕").font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("Cancel schedule")
        }
    }

    private func whenText(_ item: IosPendingSchedule) -> String {
        if item.runAtMs > 0 {
            let d = Date(timeIntervalSince1970: Double(item.runAtMs) / 1000.0)
            let f = DateFormatter()
            f.dateStyle = Calendar.current.isDateInToday(d) ? .none : .short
            f.timeStyle = .short
            return f.string(from: d)
        }
        return item.cron.isEmpty ? L("on next input") : item.cron
    }

    private func load() {
        IosSessionComposer.shared.pendingSchedules(profile: profile, sessionId: session.fullId) { list in
            DispatchQueue.main.async { items = list }
        }
    }

    private func cancel(_ item: IosPendingSchedule) {
        IosSessionComposer.shared.cancelSchedule(profile: profile, scheduleId: item.id) { err in
            DispatchQueue.main.async {
                if let err { AlertDock.shared.post(err, level: .error) }
                load()
            }
        }
    }
}

// MARK: - Terminal font dropdown (PWA fontCtrl Aa▾, D20a)

struct TerminalFontMenu: View {
    @Binding var size: Int
    var onFit: () -> Void

    private static let sizes: [Int] = Array(5...20)

    var body: some View {
        Menu {
            Button(L("Fit to width")) { onFit() }
            Divider()
            ForEach(Self.sizes, id: \.self) { px in
                Button {
                    size = px
                    UserDefaults.standard.set(px, forKey: "dw.terminal.font_size_px")
                } label: {
                    if px == size {
                        Label("\(px)px", systemImage: "checkmark")
                    } else {
                        Text("\(px)px")
                    }
                }
            }
        } label: {
            Text("Aa▾")
                .font(.system(.caption, design: .monospaced).weight(.medium))
                .foregroundStyle(DatawatchColors.onSurface)
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(Rectangle())
        }
        .accessibilityLabel(String(format: L("Terminal font size %dpx"), size))
    }
}
