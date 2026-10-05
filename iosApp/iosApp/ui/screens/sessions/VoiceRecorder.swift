import AVFoundation
import Combine
import SwiftUI

/// Records AAC audio into a temp M4A file, matching Android VoiceRecorder's format.
/// Single-use: create → start → stop/cancel. Create a new instance for each recording.
final class VoiceRecorder {
    private var recorder: AVAudioRecorder?
    private let url: URL

    static let mimeType = "audio/mp4"

    init() {
        url = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
            .appendingPathExtension("m4a")
    }

    /// Activates the audio session and starts recording. Throws on permission denial or hardware error.
    func start() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .default, options: .defaultToSpeaker)
        try session.setActive(true)

        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 16_000,
            AVNumberOfChannelsKey: 1,
            AVEncoderBitRateKey: 48_000,
            AVEncoderAudioQualityKey: AVAudioQuality.medium.rawValue,
        ]
        recorder = try AVAudioRecorder(url: url, settings: settings)
        recorder?.isMeteringEnabled = true
        recorder?.record()
    }

    /// Live input level 0…1 for the recording overlay's waveform: average
    /// power (dBFS) mapped linearly from -50 dB (silence) to 0 dB.
    func level() -> Double {
        guard let r = recorder else { return 0 }
        r.updateMeters()
        let db: Double = Double(r.averagePower(forChannel: 0))
        return max(0.0, min(1.0, (db + 50.0) / 50.0))
    }

    /// Stops recording and returns the raw audio bytes, or nil if nothing was captured.
    func stop() -> Data? {
        recorder?.stop()
        recorder = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        return try? Data(contentsOf: url)
    }

    /// Discards the recording without returning data.
    func cancel() {
        recorder?.stop()
        recorder = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        try? FileManager.default.removeItem(at: url)
    }
}

/// PWA `.voice-waveform` (style.css:2739-2760) as a live level meter: five
/// 5 pt error-red bars, 4 pt apart, 14/26/36/26/14 pt tall. Polls the
/// recorder every 70 ms and ripples the newest sample out from the centre;
/// each bar spans 40 %–100 % of its height (the CSS scaleY range).
struct VoiceLevelBars: View {
    let recorder: VoiceRecorder?

    @State private var samples: [Double] = [0, 0, 0, 0, 0]
    private let maxHeights: [Double] = [14, 26, 36, 26, 14]
    private let timer = Timer.publish(every: 0.07, on: .main, in: .common).autoconnect()

    var body: some View {
        HStack(alignment: .bottom, spacing: 4) {
            ForEach(0..<5, id: \.self) { i in
                bar(i)
            }
        }
        .frame(height: 40, alignment: .bottom)
        .onReceive(timer) { _ in tick() }
        .accessibilityHidden(true)
    }

    private func bar(_ i: Int) -> some View {
        let h: Double = maxHeights[i] * (0.4 + 0.6 * samples[i])
        return RoundedRectangle(cornerRadius: 3)
            .fill(DatawatchColors.error)
            .frame(width: 5, height: CGFloat(h))
    }

    private func tick() {
        let v: Double = recorder?.level() ?? 0.0
        let smoothed: Double = 0.6 * v + 0.4 * samples[2]
        let next: [Double] = [samples[1], samples[2], smoothed, samples[2], samples[1]]
        withAnimation(.linear(duration: 0.07)) {
            samples = next
        }
    }
}
