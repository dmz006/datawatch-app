import SwiftUI
import CoreImage.CIFilterBuiltins
import DatawatchShared

// Settings › Comms › Communication Configuration › Signal Device (PWA
// loadLinkStatus / startLinking / streamLinkEvents; Android SignalLinkingDialog).
// The server streams the `sgnl://` link URI; iOS renders it with CoreImage.

/// "Signal Device" row: link status + Link Device button (opens the QR sheet).
struct SignalDeviceSection: View {
    let profile: ServerProfile

    @State private var statusText: String = L("Checking…")
    @State private var linked = false
    @State private var showLink = false

    var body: some View {
        Section {
            HStack {
                Label("Signal Device", systemImage: "qrcode")
                    .foregroundStyle(DatawatchColors.onSurface)
                Spacer()
                Text(verbatim: statusText)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(linked ? DatawatchColors.success : DatawatchColors.onSurfaceMuted)
                    .multilineTextAlignment(.trailing)
            }
            if !linked {
                Button {
                    showLink = true
                } label: {
                    Text("Link Device").foregroundStyle(DatawatchColors.primary)
                }
            }
        }
        .listRowBackground(DatawatchColors.surface)
        .task { loadStatus() }
        .sheet(isPresented: $showLink, onDismiss: { loadStatus() }) {
            SignalLinkSheet(profile: profile)
        }
    }

    private func loadStatus() {
        IosSettingsForms.shared.signalStatus(profile: profile, onSuccess: { s in
            let text: String = s.linked
                ? L("Linked") + (s.account.isEmpty ? "" : " (" + s.account + ")")
                : L("Not linked")
            DispatchQueue.main.async {
                linked = s.linked
                statusText = text
            }
        }, onError: { _ in
            DispatchQueue.main.async {
                linked = false
                statusText = L("Unknown")
            }
        })
    }
}

/// Starts `signal-cli link` on the server and shows the streamed QR code.
private struct SignalLinkSheet: View {
    let profile: ServerProfile

    @Environment(\.dismiss) private var dismiss
    @State private var handle: IosLinkHandle?
    @State private var qrImage: UIImage?
    @State private var linked = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    content
                }
                .frame(maxWidth: .infinity)
                .padding(24)
            }
            .background(DatawatchColors.background)
            .navigationTitle("Link Signal Device")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(linked ? L("Done") : L("Cancel")) { close() }
                }
                if error != nil {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Retry Linking") { start() }
                    }
                }
            }
        }
        .dwThemed()
        .onAppear { start() }
        .onDisappear { handle?.cancel() }
    }

    @ViewBuilder
    private var content: some View {
        if linked {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 48))
                .foregroundStyle(DatawatchColors.success)
                .accessibilityHidden(true)
            Text("Device linked successfully!")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
        } else if let error {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 40))
                .foregroundStyle(DatawatchColors.error)
                .accessibilityHidden(true)
            Text(verbatim: L("Linking error") + ": " + error)
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.error)
                .multilineTextAlignment(.center)
        } else if let qrImage {
            qrView(qrImage)
            instructions
        } else {
            ProgressView()
                .padding(.top, 40)
            Text("Linking started — waiting for QR code…")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.center)
        }
    }

    private func qrView(_ image: UIImage) -> some View {
        Image(uiImage: image)
            .interpolation(.none)
            .resizable()
            .scaledToFit()
            .frame(width: 220, height: 220)
            .padding(12)
            // QR must stay dark-on-white in both themes (PWA #linkQrCode background:#fff).
            .background(Color.white, in: RoundedRectangle(cornerRadius: 8))
            .accessibilityLabel("Signal link QR code")
    }

    private var instructions: some View {
        VStack(spacing: 4) {
            Text("Open Signal on your phone")
            Text("Settings → Linked Devices → Link New Device")
        }
        .font(DatawatchFonts.bodyMedium)
        .foregroundStyle(DatawatchColors.onSurfaceMuted)
        .multilineTextAlignment(.center)
    }

    private func start() {
        handle?.cancel()
        error = nil
        qrImage = nil
        linked = false
        handle = IosSettingsForms.shared.startSignalLink(profile: profile, onQr: { uri in
            let img: UIImage? = SignalQr.image(for: uri)
            DispatchQueue.main.async {
                qrImage = img
                if img == nil { error = L("Couldn't render the QR code.") }
            }
        }, onLinked: {
            DispatchQueue.main.async { linked = true }
        }, onError: { msg in
            DispatchQueue.main.async {
                if !linked { error = msg }
            }
        })
    }

    private func close() {
        handle?.cancel()
        handle = nil
        dismiss()
    }
}

/// CoreImage QR rendering for the `sgnl://` link URI.
enum SignalQr {
    static func image(for text: String) -> UIImage? {
        guard !text.isEmpty else { return nil }
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }
        let scaled = output.transformed(by: CGAffineTransform(scaleX: 10.0, y: 10.0))
        let context = CIContext()
        guard let cg = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}
